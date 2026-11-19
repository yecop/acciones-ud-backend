package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache;

import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.port.out.CotizacionCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Caché de cotizaciones en Redis (SCRUM-24). Un HASH por ticker:
 *
 * <pre>
 * accionesud:cotizacion:AAPL → { precio, bid, ask, spread, volumen, actualizadoEn, fuente }
 * </pre>
 *
 * <p>Resiliencia: cada escritura se replica en una copia local en memoria. Si Redis no
 * está disponible, las lecturas se sirven desde esa copia y se registra una advertencia,
 * de modo que una caída de Redis tampoco rompe la lectura de cotizaciones.</p>
 */
public class RedisCotizacionCacheAdapter implements CotizacionCachePort {

    static final String PRECIO = "precio";
    static final String BID = "bid";
    static final String ASK = "ask";
    static final String SPREAD = "spread";
    static final String VOLUMEN = "volumen";
    static final String ACTUALIZADO_EN = "actualizadoEn";
    static final String FUENTE = "fuente";

    private static final Logger log = LoggerFactory.getLogger(RedisCotizacionCacheAdapter.class);

    private final StringRedisTemplate redis;
    private final String prefijo;
    private final Duration ttl;
    private final InMemoryCotizacionCacheAdapter respaldoLocal = new InMemoryCotizacionCacheAdapter();

    public RedisCotizacionCacheAdapter(StringRedisTemplate redis, String prefijo, Duration ttl) {
        this.redis = redis;
        this.prefijo = prefijo;
        this.ttl = ttl;
    }

    @Override
    public void guardar(Cotizacion cotizacion) {
        guardarTodas(List.of(cotizacion));
    }

    @Override
    public void guardarTodas(Collection<Cotizacion> cotizaciones) {
        if (cotizaciones.isEmpty()) {
            return;
        }
        cotizaciones.forEach(respaldoLocal::guardar);
        try {
            // Un solo viaje a Redis para los 10 tickers del ciclo.
            redis.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(RedisOperations<K, V> operaciones) {
                    RedisOperations<String, String> ops = (RedisOperations<String, String>) operaciones;
                    for (Cotizacion c : cotizaciones) {
                        String clave = clave(c.ticker());
                        ops.opsForHash().putAll(clave, aHash(c));
                        ops.expire(clave, ttl);
                    }
                    return null;
                }
            });
        } catch (DataAccessException e) {
            log.warn("No se pudo escribir en Redis ({}); se conserva la copia local.", e.getMessage());
        }
    }

    @Override
    public Optional<Cotizacion> obtener(String ticker) {
        if (ticker == null) {
            return Optional.empty();
        }
        String normalizado = ticker.trim().toUpperCase(Locale.ROOT);
        try {
            Map<Object, Object> hash = redis.opsForHash().entries(clave(normalizado));
            return desdeHash(normalizado, hash);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible al leer {} ({}); se usa la copia local.", normalizado, e.getMessage());
            return respaldoLocal.obtener(normalizado);
        }
    }

    @Override
    public Map<String, Cotizacion> obtenerVarias(Collection<String> tickers) {
        List<String> normalizados = tickers.stream()
                .map(t -> t.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalizados.isEmpty()) {
            return Map.of();
        }
        try {
            List<Object> hashes = redis.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(RedisOperations<K, V> operaciones) {
                    RedisOperations<String, String> ops = (RedisOperations<String, String>) operaciones;
                    normalizados.forEach(t -> ops.opsForHash().entries(clave(t)));
                    return null;
                }
            });
            Map<String, Cotizacion> resultado = new LinkedHashMap<>();
            for (int i = 0; i < normalizados.size(); i++) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> hash = (Map<Object, Object>) hashes.get(i);
                String ticker = normalizados.get(i);
                desdeHash(ticker, hash).ifPresent(c -> resultado.put(ticker, c));
            }
            return resultado;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible al leer {} tickers ({}); se usa la copia local.",
                    normalizados.size(), e.getMessage());
            return respaldoLocal.obtenerVarias(normalizados);
        }
    }

    private String clave(String ticker) {
        return prefijo + ticker;
    }

    static Map<String, String> aHash(Cotizacion c) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(PRECIO, c.precio().toPlainString());
        if (c.bid() != null) {
            hash.put(BID, c.bid().toPlainString());
        }
        if (c.ask() != null) {
            hash.put(ASK, c.ask().toPlainString());
        }
        if (c.spread() != null) {
            hash.put(SPREAD, c.spread().toPlainString());
        }
        hash.put(VOLUMEN, Long.toString(c.volumen()));
        hash.put(ACTUALIZADO_EN, c.actualizadoEn().toString());
        hash.put(FUENTE, c.fuente());
        return hash;
    }

    static Optional<Cotizacion> desdeHash(String ticker, Map<Object, Object> hash) {
        if (hash == null || hash.isEmpty() || hash.get(PRECIO) == null || hash.get(ACTUALIZADO_EN) == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Cotizacion(
                    ticker,
                    new BigDecimal(hash.get(PRECIO).toString()),
                    decimal(hash.get(BID)),
                    decimal(hash.get(ASK)),
                    decimal(hash.get(SPREAD)),
                    Long.parseLong(String.valueOf(hash.getOrDefault(VOLUMEN, "0"))),
                    Instant.parse(hash.get(ACTUALIZADO_EN).toString()),
                    String.valueOf(hash.getOrDefault(FUENTE, "DESCONOCIDA"))));
        } catch (RuntimeException e) {
            log.warn("Entrada de caché corrupta para {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    private static BigDecimal decimal(Object valor) {
        return valor == null ? null : new BigDecimal(valor.toString());
    }
}
