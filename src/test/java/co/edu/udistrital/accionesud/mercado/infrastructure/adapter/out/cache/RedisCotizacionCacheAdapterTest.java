package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache;

import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Si Redis se cae, la lectura de cotizaciones no falla: se sirve la copia local.
 * Se usa un cliente Redis real apuntando a un puerto sin servidor (conexión rechazada).
 */
class RedisCotizacionCacheAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-01-05T15:00:00Z");

    @Test
    void conRedisCaidoLeeDeLaCopiaLocal() throws IOException {
        int puertoLibre;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoLibre = socket.getLocalPort();
        }
        LettuceConnectionFactory conexion = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", puertoLibre),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        conexion.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(conexion);
            RedisCotizacionCacheAdapter cache =
                    new RedisCotizacionCacheAdapter(redis, "accionesud:cotizacion:", Duration.ofHours(1));

            cache.guardar(Cotizacion.de("AAPL", new BigDecimal("190.00"), null, null, 10, AHORA, "ALPACA_IEX"));

            assertThat(cache.obtener("AAPL")).get().extracting(Cotizacion::precio)
                    .isEqualTo(new BigDecimal("190.00"));
            assertThat(cache.obtenerVarias(List.of("AAPL", "MSFT"))).containsOnlyKeys("AAPL");
        } finally {
            conexion.destroy();
        }
    }

    @Test
    void conviertePorIdaYVueltaElHash() {
        Cotizacion original = Cotizacion.de("NVDA", new BigDecimal("118.40"), new BigDecimal("118.38"),
                new BigDecimal("118.42"), 999, AHORA, "ALPACA_IEX");

        Cotizacion leida = RedisCotizacionCacheAdapter.desdeHash("NVDA",
                new HashMap<>(RedisCotizacionCacheAdapter.aHash(original))).orElseThrow();

        assertThat(leida).isEqualTo(original);
    }
}
