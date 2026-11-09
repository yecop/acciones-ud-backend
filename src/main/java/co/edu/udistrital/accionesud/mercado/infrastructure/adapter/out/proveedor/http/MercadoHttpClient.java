package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http;

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
import java.util.Map;
import java.util.Optional;

/**
 * Cliente HTTP ligero y reutilizable para consultar las APIs de proveedores de
 * datos de mercado. Usa el {@link HttpClient} del JDK (sin dependencias extra) y
 * Jackson (ya disponible en Spring Boot) para parsear las respuestas JSON.
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

    public MercadoHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Ejecuta un GET y devuelve el cuerpo como árbol JSON.
     *
     * @param url      URL completa a consultar.
     * @param cabeceras cabeceras HTTP (puede estar vacío).
     * @return el JSON parseado, o vacío si la consulta falla.
     */
    public Optional<JsonNode> getJson(String url, Map<String, String> cabeceras) {
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
}
