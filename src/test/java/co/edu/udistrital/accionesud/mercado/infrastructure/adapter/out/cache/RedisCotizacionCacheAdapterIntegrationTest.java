package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache;

import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración real contra Redis 7 (Testcontainers). Se omite automáticamente si no hay
 * Docker disponible; en el CI (ubuntu-latest) sí se ejecuta.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisCotizacionCacheAdapterIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory conexion;
    private static StringRedisTemplate redis;
    private RedisCotizacionCacheAdapter cache;

    private static final Instant AHORA = Instant.parse("2026-01-05T15:00:00Z");

    @BeforeAll
    static void conectar() {
        conexion = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        conexion.afterPropertiesSet();
        conexion.start();
        redis = new StringRedisTemplate(conexion);
    }

    @AfterAll
    static void desconectar() {
        conexion.destroy();
    }

    @BeforeEach
    void limpiar() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        cache = new RedisCotizacionCacheAdapter(redis, "accionesud:cotizacion:", Duration.ofHours(1));
    }

    @Test
    void guardaUnHashPorTickerConTodosLosCampos() {
        cache.guardar(Cotizacion.de("AAPL", new BigDecimal("190.12"), new BigDecimal("190.10"),
                new BigDecimal("190.15"), 1000, AHORA, "ALPACA_IEX"));

        Map<Object, Object> hash = redis.opsForHash().entries("accionesud:cotizacion:AAPL");
        assertThat(hash).containsEntry("precio", "190.12")
                .containsEntry("bid", "190.10")
                .containsEntry("ask", "190.15")
                .containsEntry("spread", "0.05")
                .containsEntry("volumen", "1000")
                .containsEntry("actualizadoEn", AHORA.toString())
                .containsEntry("fuente", "ALPACA_IEX");
        assertThat(redis.getExpire("accionesud:cotizacion:AAPL")).isPositive();
    }

    @Test
    void leeUnoYVariosTickers() {
        cache.guardarTodas(List.of(
                Cotizacion.de("AAPL", BigDecimal.TEN, null, null, 1, AHORA, "ALPACA_IEX"),
                Cotizacion.de("MSFT", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, 2, AHORA, "ALPACA_IEX")));

        assertThat(cache.obtener("aapl")).get().extracting(Cotizacion::precio).isEqualTo(BigDecimal.TEN);
        assertThat(cache.obtener("NVDA")).isEmpty();
        assertThat(cache.obtenerVarias(List.of("AAPL", "MSFT", "NVDA"))).containsOnlyKeys("AAPL", "MSFT");
    }

    @Test
    void sobreviveUnReinicioDelAdaptador() {
        cache.guardar(Cotizacion.de("TSLA", BigDecimal.TEN, null, null, 1, AHORA, "ALPACA_IEX"));

        RedisCotizacionCacheAdapter otraInstancia =
                new RedisCotizacionCacheAdapter(redis, "accionesud:cotizacion:", Duration.ofHours(1));
        assertThat(otraInstancia.obtener("TSLA")).isPresent();
    }
}
