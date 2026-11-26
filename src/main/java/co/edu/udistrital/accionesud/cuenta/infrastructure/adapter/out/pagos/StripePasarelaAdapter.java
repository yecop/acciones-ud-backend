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
 *   <li>{@code Idempotency-Key} = {@code recarga-<movimientoId>}: un reintento no crea otra sesión.</li>
 *   <li>Los errores (sin respuesta, 429, 5xx o un rechazo) se traducen a
 *   {@link PasarelaNoDisponibleException}; el cuerpo del error se registra sin la clave.</li>
 * </ul>
 */
public class StripePasarelaAdapter implements PasarelaPagosPort {

    private static final Logger log = LoggerFactory.getLogger(StripePasarelaAdapter.class);
    static final String NOMBRE_PRODUCTO = "Recarga de saldo Acciones UD";

    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;
    private final String secretKey;
    private final String urlExito;
    private final String urlCancelacion;
    private final Duration timeout;

    public StripePasarelaAdapter(HttpClient http, ObjectMapper json, String baseUrl, String secretKey,
                                 String urlExito, String urlCancelacion, Duration timeout) {
        this.http = Objects.requireNonNull(http);
        this.json = Objects.requireNonNull(json);
        this.baseUrl = quitarBarraFinal(Objects.requireNonNull(baseUrl));
        this.secretKey = Objects.requireNonNull(secretKey);
        this.urlExito = Objects.requireNonNull(urlExito);
        this.urlCancelacion = Objects.requireNonNull(urlCancelacion);
        this.timeout = Objects.requireNonNull(timeout);
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

        HttpRequest solicitud = base("/v1/checkout/sessions")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", "recarga-" + movimiento)
                .POST(HttpRequest.BodyPublishers.ofString(formulario(campos)))
                .build();
        return sesion(enviar(solicitud, "crear la sesión de pago"));
    }

    @Override
    public Optional<SesionPago> consultarSesion(String referencia) {
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
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PasarelaNoDisponibleException("Stripe no respondió al " + accion, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PasarelaNoDisponibleException("Se interrumpió la llamada a Stripe", e);
        }
        if (respuesta.statusCode() / 100 != 2) {
            log.warn("Stripe respondió {} al {}: {}", respuesta.statusCode(), accion, mensajeDeError(respuesta.body()));
            throw new PasarelaNoDisponibleException("Stripe rechazó la solicitud (" + respuesta.statusCode() + ")");
        }
        try {
            return json.readTree(respuesta.body());
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

    private String mensajeDeError(String cuerpo) {
        try {
            return json.readTree(cuerpo).path("error").path("message").asText("(sin mensaje)");
        } catch (IOException e) {
            return "(respuesta ilegible)";
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
