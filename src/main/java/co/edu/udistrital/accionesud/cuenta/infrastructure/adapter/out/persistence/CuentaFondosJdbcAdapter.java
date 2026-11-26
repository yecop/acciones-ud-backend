package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.cuenta.application.dto.PaginaMovimientos;
import co.edu.udistrital.accionesud.cuenta.application.port.out.CuentaFondosPort;
import co.edu.udistrital.accionesud.cuenta.domain.exception.ConflictoIdempotenciaException;
import co.edu.udistrital.accionesud.cuenta.domain.model.EstadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.domain.model.TipoMovimiento;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Saldo en {@code cuenta_corretaje} y movimientos en {@code movimiento_fondos} (V3 y V14).
 *
 * <p>La concurrencia se resuelve en PostgreSQL: el débito es un {@code UPDATE ... WHERE
 * saldo_disponible >= monto}, que bloquea la fila y vuelve a evaluar la condición si otra
 * transacción la cambió; además el CHECK {@code saldo_disponible >= 0} impide un saldo negativo.
 * La clave de idempotencia es única en la tabla.</p>
 */
public class CuentaFondosJdbcAdapter implements CuentaFondosPort {

    private static final String COLUMNAS = """
            id, cuenta_id, tipo, estado, monto, moneda, creado_en, completado_en, trace_id""";

    private static final RowMapper<MovimientoFondos> MOVIMIENTO = CuentaFondosJdbcAdapter::movimiento;

    private final JdbcTemplate jdbc;

    public CuentaFondosJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<UUID> cuentaDe(UUID inversionistaId) {
        return jdbc.query("SELECT id FROM cuenta_corretaje WHERE inversionista_id = ?",
                (rs, i) -> rs.getObject("id", UUID.class), inversionistaId).stream().findFirst();
    }

    @Override
    public Saldo saldo(UUID cuentaId) {
        return jdbc.queryForObject("SELECT saldo_disponible, saldo_reservado FROM cuenta_corretaje WHERE id = ?",
                (rs, i) -> new Saldo(rs.getBigDecimal("saldo_disponible"), rs.getBigDecimal("saldo_reservado")),
                cuentaId);
    }

    @Override
    public boolean aplicarSaldoInicial(MovimientoFondos deposito, String claveIdempotencia) {
        // Solo si la cuenta no tiene movimientos; la clave única resuelve dos llamadas simultáneas.
        int filas = jdbc.update("""
                INSERT INTO movimiento_fondos (id, cuenta_id, tipo, estado, monto, moneda, clave_idempotencia,
                                               trace_id, creado_en, completado_en)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM movimiento_fondos WHERE cuenta_id = ?)
                ON CONFLICT (clave_idempotencia) DO NOTHING""",
                deposito.id(), deposito.cuentaId(), deposito.tipo().name(), deposito.estado().name(),
                deposito.monto(), deposito.moneda(), claveIdempotencia, deposito.traceId(),
                Timestamp.from(deposito.creadoEn()), marca(deposito.completadoEn()), deposito.cuentaId());
        if (filas == 0) {
            return false;
        }
        acreditar(deposito.cuentaId(), deposito.monto());
        return true;
    }

    @Override
    public BigDecimal acreditar(UUID cuentaId, BigDecimal monto) {
        return jdbc.queryForObject("""
                UPDATE cuenta_corretaje SET saldo_disponible = saldo_disponible + ?
                WHERE id = ? RETURNING saldo_disponible""", BigDecimal.class, monto, cuentaId);
    }

    @Override
    public Optional<BigDecimal> debitar(UUID cuentaId, BigDecimal monto) {
        return jdbc.query("""
                UPDATE cuenta_corretaje SET saldo_disponible = saldo_disponible - ?
                WHERE id = ? AND saldo_disponible >= ? RETURNING saldo_disponible""",
                (rs, i) -> rs.getBigDecimal(1), monto, cuentaId, monto).stream().findFirst();
    }

    @Override
    public void registrar(MovimientoFondos m, String claveIdempotencia) {
        try {
            jdbc.update("""
                    INSERT INTO movimiento_fondos (id, cuenta_id, tipo, estado, monto, moneda, clave_idempotencia,
                                                   trace_id, creado_en, completado_en)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    m.id(), m.cuentaId(), m.tipo().name(), m.estado().name(), m.monto(), m.moneda(),
                    claveIdempotencia, m.traceId(), Timestamp.from(m.creadoEn()), marca(m.completadoEn()));
        } catch (DuplicateKeyException e) {
            throw new ConflictoIdempotenciaException();
        }
    }

    @Override
    public Optional<MovimientoFondos> buscarPorClave(String claveIdempotencia) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM movimiento_fondos WHERE clave_idempotencia = ?",
                MOVIMIENTO, claveIdempotencia).stream().findFirst();
    }

    @Override
    public PaginaMovimientos listar(UUID cuentaId, TipoMovimiento tipo, int pagina, int tamano) {
        String filtro = "WHERE cuenta_id = ?" + (tipo == null ? "" : " AND tipo = ?");
        List<Object> parametros = new ArrayList<>(List.of(cuentaId));
        if (tipo != null) {
            parametros.add(tipo.name());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM movimiento_fondos " + filtro, Long.class,
                parametros.toArray());
        parametros.add(tamano);
        parametros.add((long) pagina * tamano);
        List<MovimientoFondos> contenido = jdbc.query("SELECT " + COLUMNAS + " FROM movimiento_fondos " + filtro
                        + " ORDER BY creado_en DESC, id DESC LIMIT ? OFFSET ?",
                MOVIMIENTO, parametros.toArray());
        return PaginaMovimientos.de(contenido, pagina, tamano, total == null ? 0 : total);
    }

    private static MovimientoFondos movimiento(ResultSet rs, int fila) throws SQLException {
        Timestamp completado = rs.getTimestamp("completado_en");
        return new MovimientoFondos(
                rs.getObject("id", UUID.class),
                rs.getObject("cuenta_id", UUID.class),
                TipoMovimiento.valueOf(rs.getString("tipo")),
                EstadoMovimiento.valueOf(rs.getString("estado")),
                rs.getBigDecimal("monto"),
                rs.getString("moneda"),
                rs.getTimestamp("creado_en").toInstant(),
                completado == null ? null : completado.toInstant(),
                rs.getString("trace_id"));
    }

    private static Timestamp marca(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
