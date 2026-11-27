package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.ordenes.application.port.out.PosicionDisponiblePort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Consulta la posición persistida, descontando las acciones reservadas (V14). */
public class PosicionDisponibleJdbcAdapter implements PosicionDisponiblePort {

    private final JdbcTemplate jdbc;

    public PosicionDisponibleJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public long cantidadDisponible(UUID inversionistaId, String ticker) {
        Long disponible = jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.cantidad - p.cantidad_reservada), 0)
                FROM posicion p
                JOIN cuenta_corretaje c ON c.id = p.cuenta_id
                JOIN accion a ON a.id = p.accion_id
                WHERE c.inversionista_id = ? AND upper(a.ticker) = upper(?)
                """, Long.class, inversionistaId, ticker);
        return disponible == null ? 0 : disponible;
    }
}
