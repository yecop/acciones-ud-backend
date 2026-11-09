package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http;

import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente HTTP ligero y reutilizable para consultar las APIs de proveedores de
 * datos de mercado. Usa el {@link HttpClient} del JDK (sin dependencias extra) y
 * Jackson (ya disponible en Spring Boot) para parsear las respuestas JSON.
 *
 * <p>Incorpora una caché en memoria con TTL configurable para no exceder los
 * límites de los planes gratuitos (rate limits) ni golpear las APIs en cada
 * request del widget. El TTL se controla con el {@link RelojPort}, lo que permite
 * verificar la expiración de forma determinista en pruebas.</p>
 *
 * <p>Toda la comunicación es tolerante a fallos: cualquier error (timeout, código
 * no 2xx, JSON inválido) se traduce en un {@link Optional#empty()} para que la capa
 * superior pueda aplicar su estrategia de respaldo o delegar al cálculo por horario.</p>
 */
@Component
public class MercadoHttpClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoHttpClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RelojPort reloj;
    private final MercadosProperties.Cache cacheConfig;
    private final ConcurrentHashMap<String, Entrada> cache = new ConcurrentHashMap<>();

    public MercadoHttpClient(ObjectMapper objectMapper,
                             RelojPort reloj,
                             MercadosProperties properties) {
        this.objectMapper = objectMapper;
        this.reloj = reloj;
        this.cacheConfig = properties.getCache();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Ejecuta un GET y devuelve el cuerpo como árbol JSON, usando la caché con TTL.
     *
     * @param url       URL completa a consultar (actúa como clave de caché).
     * @param cabeceras cabeceras HTTP (puede estar vacío).
     * @return el JSON parseado, o vacío si la consulta falla.
     */
    public Optional<JsonNode> getJson(String url, Map<String, String> cabeceras) {
        if (cacheConfig.isEnabled()) {
            Optional<JsonNode> enCache = leerDeCache(url);
            if (enCache.isPresent()) {
                log.debug("Cache HIT para {}", url);
                return enCache;
            }
        }

        Optional<JsonNode> respuesta = ejecutarGet(url, cabeceras);
        if (cacheConfig.isEnabled() && respuesta.isPresent()) {
            guardarEnCache(url, respuesta.get());
        }
        return respuesta;
    }

    /** Invalida toda la caché (útil para pruebas o refresco manual). */
    public void limpiarCache() {
        cache.clear();
    }

    private Optional<JsonNode> leerDeCache(String url) {
        Entrada entrada = cache.get(url);
        if (entrada == null) {
            return Optional.empty();
        }
        if (reloj.ahora().isAfter(entrada.expiraEn())) {
            cache.remove(url, entrada);
            return Optional.empty();
        }
        return Optional.of(entrada.valor());
    }

    private void guardarEnCache(String url, JsonNode valor) {
        Instant expiraEn = reloj.ahora().plusSeconds(cacheConfig.getTtlSegundos());
        cache.put(url, new Entrada(valor, expiraEn));
    }

    private Optional<JsonNode> ejecutarGet(String url, Map<String, String> cabeceras) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET();
            cabeceras.forEach(builder::header);

            HttpResponse<String> respuesta =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (respuesta.statusCode() / 100 != 2) {
                log.warn("Respuesta no exitosa de {} (status {})", url, respuesta.statusCode());
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(respuesta.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Consulta interrumpida a {}", url);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error consultando {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /** Entrada de caché: valor JSON y su instante de expiración. */
    private record Entrada(JsonNode valor, Instant expiraEn) {
    }
}
