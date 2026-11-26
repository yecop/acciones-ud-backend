package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.pagos;

import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoDisponibleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stripe Checkout por su API REST ({@code POST /v1/checkout/sessions}), sin SDK.
 *
 * <ul>
 *   <li>La sesión lleva el id del movimiento en {@code client_reference_id} y en {@code metadata};
 *   el webhook lo usa para encontrar el depósito.</li>
 *   <li>{@code Idempotency-Key} = {@code recarga-<movimientoId>} (menos de 255 caracteres): un
 *   reintento con los mismos parámetros no crea otra sesión.</li>
 *   <li><b>Reintentos</b> (guía de errores de Stripe): se reintenta con la misma clave y espera
 *   exponencial solo lo que es seguro repetir: error de conexión o tiempo de espera, 429 (demasiadas
 *   solicitudes) y 409 por una solicitud concurrente. No se reintentan los errores de la solicitud
 *   (400, 401, 403, 404, {@code idempotency_error}) ni un 5xx: Stripe guarda la respuesta 500
 *   asociada a la clave, así que repetirla devolvería lo mismo.</li>
 *   <li>Todo error se traduce a {@link PasarelaNoDisponibleException} y se registra con el
 *   {@code type}, {@code code}, {@code param} y el {@code Request-Id} de Stripe, nunca con la clave.</li>
 * </ul>
 */
public class StripePasarelaAdapter implements PasarelaPagosPort {

    private static final Logger log = LoggerFactory.getLogger(StripePasarelaAdapter.class);
    static final String NOMBRE_PRODUCTO = "Recarga de saldo Acciones UD";

    /** Espera entre reintentos; se reemplaza en pruebas para no dormir de verdad. */
    @FunctionalInterface
    public interface Espera {
        void dormir(Duration duracion) throws InterruptedException;
    }

    /** Reintentos ante fallas transitorias: cuántos y la espera del primero (luego se duplica). */
    public record Reintentos(int maximo, Duration esperaInicial) {
        public static final Reintentos POR_DEFECTO = new Reintentos(2, Duration.ofMillis(500));

        public Reintentos {
            if (maximo < 0) {
                throw new IllegalArgumentException("Los reintentos no pueden ser negativos");
            }
            Objects.requireNonNull(esperaInicial);
        }
    }

    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;
    private final String secretKey;
    private final String urlExito;
    private final String urlCancelacion;
    private final Duration timeout;
    private final Reintentos reintentos;
    private final Espera espera;

    public StripePasarelaAdapter(HttpClient http, ObjectMapper json, String baseUrl, String secretKey,
                                 String urlExito, String urlCancelacion, Duration timeout) {
        this(http, json, baseUrl, secretKey, urlExito, urlCancelacion, timeout, Reintentos.POR_DEFECTO,
                d -> Thread.sleep(d.toMillis()));
    }

    public StripePasarelaAdapter(HttpClient http, ObjectMapper json, String baseUrl, String secretKey,
                                 String urlExito, String urlCancelacion, Duration timeout,
                                 Reintentos reintentos, Espera espera) {
        this.http = Objects.requireNonNull(http);
        this.json = Objects.requireNonNull(json);
        this.baseUrl = quitarBarraFinal(Objects.requireNonNull(baseUrl));
        this.secretKey = Objects.requireNonNull(secretKey);
        this.urlExito = Objects.requireNonNull(urlExito);
        this.urlCancelacion = Objects.requireNonNull(urlCancelacion);
        this.timeout = Objects.requireNonNull(timeout);
        this.reintentos = Objects.requireNonNull(reintentos);
        this.espera = Objects.requireNonNull(espera);
        if (secretKey.isBlank()) {
            throw new IllegalStateException("Falta la clave secreta de Stripe (STRIPE_SECRET_KEY)");
        }
    }

    @Override
    public boolean configurada() {
        return true;
    }

    @Override
    public SesionPago crearSesion(SolicitudPago s) {
        long centavos = s.monto().movePointRight(2).longValueExact();
        String movimiento = s.movimientoId().toString();
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("mode", "payment");
        campos.put("client_reference_id", movimiento);
        campos.put("line_items[0][quantity]", "1");
        campos.put("line_items[0][price_data][currency]", s.moneda().toLowerCase(Locale.ROOT));
        campos.put("line_items[0][price_data][unit_amount]", Long.toString(centavos));
        campos.put("line_items[0][price_data][product_data][name]", NOMBRE_PRODUCTO);
        campos.put("metadata[movimientoId]", movimiento);
        campos.put("metadata[cuentaId]", s.cuentaId().toString());
        campos.put("payment_intent_data[metadata][movimientoId]", movimiento);
        campos.put("success_url", conParametro(urlExito, "movimiento", movimiento));
        campos.put("cancel_url", conParametro(urlCancelacion, "movimiento", movimiento));

        // La misma solicitud (misma clave y mismos parámetros) en cada intento.
        HttpRequest solicitud = base("/v1/checkout/sessions")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", "recarga-" + movimiento)
                .POST(HttpRequest.BodyPublishers.ofString(formulario(campos)))
                .build();
        return sesion(enviar(solicitud, "crear la sesión de pago"));
    }

    @Override
    public Optional<SesionPago> consultarSesion(String referencia) {
        // GET: Stripe no usa Idempotency-Key en lecturas.
        HttpRequest solicitud = base("/v1/checkout/sessions/" + URLEncoder.encode(referencia, StandardCharsets.UTF_8))
                .GET().build();
        try {
            return Optional.of(sesion(enviar(solicitud, "consultar la sesión de pago")));
        } catch (PasarelaNoDisponibleException e) {
            log.warn("No se pudo consultar la sesión {} en Stripe: {}", referencia, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpRequest.Builder base(String ruta) {
        return HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .timeout(timeout)
                .header("Authorization", "Bearer " + secretKey)
                .header("Accept", "application/json");
    }

    private JsonNode enviar(HttpRequest solicitud, String accion) {
        Duration pausa = reintentos.esperaInicial();
        for (int intento = 0; ; intento++) {
            boolean quedanIntentos = intento < reintentos.maximo();
            HttpResponse<String> respuesta;
            try {
                respuesta = http.send(solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // Resultado incierto (Stripe pudo haberla recibido): se repite con la misma clave.
                if (quedanIntentos) {
                    log.warn("Sin respuesta de Stripe al {} (intento {}): {}; se reintenta", accion, intento + 1,
                            e.getMessage());
                    pausa = esperar(pausa);
                    continue;
                }
                throw new PasarelaNoDisponibleException("Stripe no respondió al " + accion, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PasarelaNoDisponibleException("Se interrumpió la llamada a Stripe", e);
            }

            int status = respuesta.statusCode();
            if (status / 100 == 2) {
                return leer(respuesta.body());
            }
            ErrorStripe error = ErrorStripe.de(json, respuesta);
            if (esReintentable(status, error) && quedanIntentos) {
                log.warn("Stripe respondió {} al {} (intento {}, {}); se reintenta", status, accion, intento + 1, error);
                pausa = esperar(pausa);
                continue;
            }
            if (status == 401 || status == 403) {
                log.error("Stripe rechazó la clave al {} ({}): revisa STRIPE_SECRET_KEY y sus permisos ({})",
                        accion, status, error);
            } else {
                log.warn("Stripe respondió {} al {} ({})", status, accion, error);
            }
            throw new PasarelaNoDisponibleException("Stripe rechazó la solicitud (" + status
                    + (error.type() == null ? "" : ", " + error.type()) + ")");
        }
    }

    /**
     * Un 429 o el 409 de una solicitud concurrente se pueden repetir. Un {@code idempotency_error}
     * nunca, venga con el status que venga: repetir la misma clave con los mismos datos fallaría igual.
     */
    static boolean esReintentable(int status, ErrorStripe error) {
        if ("idempotency_error".equals(error.type())) {
            return false;
        }
        return status == 429 || status == 409;
    }

    private Duration esperar(Duration pausa) {
        try {
            espera.dormir(pausa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PasarelaNoDisponibleException("Se interrumpió la espera para reintentar en Stripe", e);
        }
        return pausa.multipliedBy(2);
    }

    private JsonNode leer(String cuerpo) {
        try {
            return json.readTree(cuerpo);
        } catch (IOException e) {
            throw new PasarelaNoDisponibleException("Stripe devolvió una respuesta ilegible", e);
        }
    }

    private static SesionPago sesion(JsonNode cuerpo) {
        String id = cuerpo.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new PasarelaNoDisponibleException("Stripe no devolvió el id de la sesión");
        }
        String url = cuerpo.path("url").isTextual() ? cuerpo.path("url").asText() : null;
        Instant expira = cuerpo.path("expires_at").canConvertToLong()
                ? Instant.ofEpochSecond(cuerpo.path("expires_at").asLong()) : null;
        boolean abierta = "open".equals(cuerpo.path("status").asText());
        return new SesionPago(id, abierta ? url : null, expira, abierta);
    }

    /** Campos del objeto de error de Stripe útiles para el log (sin datos de la tarjeta ni la clave). */
    record ErrorStripe(String type, String code, String param, String message, String requestId) {

        static ErrorStripe de(ObjectMapper json, HttpResponse<String> respuesta) {
            String requestId = respuesta.headers().firstValue("Request-Id").orElse(null);
            try {
                JsonNode error = json.readTree(respuesta.body()).path("error");
                return new ErrorStripe(texto(error, "type"), texto(error, "code"), texto(error, "param"),
                        texto(error, "message"), requestId);
            } catch (IOException | RuntimeException e) {
                return new ErrorStripe(null, null, null, "(respuesta ilegible)", requestId);
            }
        }

        private static String texto(JsonNode nodo, String campo) {
            return nodo.path(campo).isTextual() ? nodo.path(campo).asText() : null;
        }

        @Override
        public String toString() {
            return "type=" + type + ", code=" + code + ", param=" + param + ", requestId=" + requestId
                    + ", message=" + message;
        }
    }

    static String formulario(Map<String, String> campos) {
        return campos.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String conParametro(String url, String nombre, String valor) {
        return url + (url.contains("?") ? "&" : "?") + nombre + "=" + URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static String quitarBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
