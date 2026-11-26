package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia;

import co.edu.udistrital.accionesud.notificaciones.application.port.out.EnvioRepositoryPort;
import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.EnvioNotificacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Envíos por canal externo en PostgreSQL (tabla {@code notificacion_envio}, V13). */
public class EnvioJdbcAdapter implements EnvioRepositoryPort {

    private static final String COLUMNAS =
            "notificacion_id, canal_id, estado, intentos, ultimo_error, proximo_intento, enviado_en";

    private static final RowMapper<EnvioNotificacion> MAPEO = (rs, i) -> new EnvioNotificacion(
            rs.getObject("notificacion_id", UUID.class),
            CanalNotificacion.desdeId(rs.getInt("canal_id")),
            EnvioNotificacion.Estado.valueOf(rs.getString("estado")),
            rs.getInt("intentos"),
            rs.getString("ultimo_error"),
            instante(rs.getTimestamp("proximo_intento")),
            instante(rs.getTimestamp("enviado_en")));

    private final JdbcTemplate jdbc;

    public EnvioJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void guardarSiNoExiste(EnvioNotificacion e) {
        jdbc.update("INSERT INTO notificacion_envio (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT DO NOTHING",
                e.notificacionId(), e.canal().id(), e.estado().name(), e.intentos(), e.ultimoError(),
                marca(e.proximoIntento()), marca(e.enviadoEn()));
    }

    @Override
    public void actualizar(EnvioNotificacion e) {
        jdbc.update("UPDATE notificacion_envio SET estado = ?, intentos = ?, ultimo_error = ?, proximo_intento = ?, "
                        + "enviado_en = ?, actualizado_en = now() WHERE notificacion_id = ? AND canal_id = ?",
                e.estado().name(), e.intentos(), e.ultimoError(), marca(e.proximoIntento()), marca(e.enviadoEn()),
                e.notificacionId(), e.canal().id());
    }

    @Override
    public Optional<EnvioNotificacion> buscar(UUID notificacionId, CanalNotificacion canal) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM notificacion_envio WHERE notificacion_id = ? AND canal_id = ?",
                MAPEO, notificacionId, canal.id()).stream().findFirst();
    }

    @Override
    public List<EnvioNotificacion> reclamarPendientes(Instant ahora, Instant reservadoHasta, int limite) {
        // SKIP LOCKED: dos instancias no toman el mismo envío; la reserva lo saca de la cola mientras se envía.
        return jdbc.query("UPDATE notificacion_envio SET proximo_intento = ?, actualizado_en = now() "
                        + "WHERE (notificacion_id, canal_id) IN ("
                        + "  SELECT notificacion_id, canal_id FROM notificacion_envio"
                        + "  WHERE estado = 'PENDIENTE' AND proximo_intento <= ?"
                        + "  ORDER BY proximo_intento LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "RETURNING " + COLUMNAS,
                MAPEO, Timestamp.from(reservadoHasta), Timestamp.from(ahora), limite);
    }

    private static Timestamp marca(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    private static Instant instante(Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }
}
