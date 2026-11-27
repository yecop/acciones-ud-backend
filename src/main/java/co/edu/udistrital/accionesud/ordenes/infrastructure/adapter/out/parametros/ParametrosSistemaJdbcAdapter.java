package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.out.parametros;

import co.edu.udistrital.accionesud.ordenes.application.port.out.ParametrosPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Lee los parámetros de comisión y mantiene una caché breve para evitar una consulta por simulación. */
public class ParametrosSistemaJdbcAdapter implements ParametrosPort {

    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final Duration ttl;
    private volatile Map<String, BigDecimal> cache = Map.of();
    private volatile Instant venceEn = Instant.MIN;

    public ParametrosSistemaJdbcAdapter(JdbcTemplate jdbc, Clock reloj, Duration ttl) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.reloj = Objects.requireNonNull(reloj);
        this.ttl = Objects.requireNonNull(ttl);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("El TTL de parámetros debe ser positivo");
        }
    }

    @Override
    public Optional<BigDecimal> obtenerDecimal(String clave) {
        Objects.requireNonNull(clave);
        return Optional.ofNullable(valores().get(clave));
    }

    private Map<String, BigDecimal> valores() {
        Instant ahora = reloj.instant();
        if (ahora.isBefore(venceEn)) {
            return cache;
        }
        synchronized (this) {
            ahora = reloj.instant();
            if (!ahora.isBefore(venceEn)) {
                Map<String, BigDecimal> nuevos = new HashMap<>();
                jdbc.query("SELECT clave, valor FROM parametro_sistema WHERE clave LIKE 'comision.%'",
                        (RowCallbackHandler) rs -> {
                            nuevos.put(rs.getString("clave"), new BigDecimal(rs.getString("valor")));
                        });
                cache = Map.copyOf(nuevos);
                venceEn = ahora.plus(ttl);
            }
            return cache;
        }
    }
}
