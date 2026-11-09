package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http;

import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la caché con TTL de {@link MercadoHttpClient}.
 *
 * <p>Levanta un servidor HTTP local (del JDK) que cuenta cuántas veces se le llama,
 * y usa un {@link RelojPort} controlable para simular el paso del tiempo y validar
 * la expiración por TTL de forma determinista.</p>
 */
class MercadoHttpClientTest {

    private HttpServer servidor;
    private AtomicInteger contadorPeticiones;
    private String url;

    /** Reloj simulado: su instante se puede avanzar manualmente en la prueba. */
    private static final class RelojSimulado implements RelojPort {
        private Instant actual;

        RelojSimulado(Instant inicial) {
            this.actual = inicial;
        }

        void avanzarSegundos(long segundos) {
            actual = actual.plusSeconds(segundos);
        }

        @Override
        public Instant ahora() {
            return actual;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        contadorPeticiones = new AtomicInteger();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/clock", intercambio -> {
            contadorPeticiones.incrementAndGet();
            byte[] cuerpo = "{\"is_open\":true}".getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream os = intercambio.getResponseBody()) {
                os.write(cuerpo);
            }
        });
        servidor.start();
        url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/clock";
    }

    @AfterEach
    void tearDown() {
        servidor.stop(0);
    }

    private MercadoHttpClient cliente(RelojPort reloj, boolean cacheEnabled, long ttl) {
        MercadosProperties props = new MercadosProperties();
        props.getCache().setEnabled(cacheEnabled);
        props.getCache().setTtlSegundos(ttl);
        return new MercadoHttpClient(new ObjectMapper(), reloj, props);
    }

    @Test
    @DisplayName("Dentro del TTL, la segunda llamada se sirve desde caché (una sola petición HTTP)")
    void sirveDesdeCacheDentroDelTtl() {
        RelojSimulado reloj = new RelojSimulado(Instant.parse("2026-01-05T12:00:00Z"));
        MercadoHttpClient cliente = cliente(reloj, true, 60);

        Optional<JsonNode> r1 = cliente.getJson(url, Map.of());
        Optional<JsonNode> r2 = cliente.getJson(url, Map.of());

        assertThat(r1).isPresent();
        assertThat(r2).isPresent();
        assertThat(r1.get().get("is_open").asBoolean()).isTrue();
        assertThat(contadorPeticiones.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tras expirar el TTL, se vuelve a consultar la API (dos peticiones HTTP)")
    void reconsultaTrasExpirarTtl() {
        RelojSimulado reloj = new RelojSimulado(Instant.parse("2026-01-05T12:00:00Z"));
        MercadoHttpClient cliente = cliente(reloj, true, 60);

        cliente.getJson(url, Map.of());
        reloj.avanzarSegundos(61); // supera el TTL
        cliente.getJson(url, Map.of());

        assertThat(contadorPeticiones.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Con la caché deshabilitada, cada llamada consulta la API")
    void sinCacheConsultaSiempre() {
        RelojSimulado reloj = new RelojSimulado(Instant.parse("2026-01-05T12:00:00Z"));
        MercadoHttpClient cliente = cliente(reloj, false, 60);

        cliente.getJson(url, Map.of());
        cliente.getJson(url, Map.of());

        assertThat(contadorPeticiones.get()).isEqualTo(2);
    }
}
