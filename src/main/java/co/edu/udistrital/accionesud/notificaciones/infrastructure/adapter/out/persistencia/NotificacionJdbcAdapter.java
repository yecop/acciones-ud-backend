package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia;

import co.edu.udistrital.accionesud.notificaciones.application.dto.FiltroNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.dto.PaginaNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.NotificacionRepositoryPort;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.TipoNotificacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bandeja de notificaciones en PostgreSQL (tabla {@code notificacion}, V13). */
public class NotificacionJdbcAdapter implements NotificacionRepositoryPort {

    private static final String COLUMNAS = "id, usuario_id, evento_id, tipo, titulo, mensaje, referencia_id, "
            + "leida, creada_en, leida_en, trace_id";

    private static final RowMapper<Notificacion> MAPEO = (rs, i) -> new Notificacion(
            rs.getObject("id", UUID.class),
            rs.getObject("usuario_id", UUID.class),
            rs.getObject("evento_id", UUID.class),
            TipoNotificacion.valueOf(rs.getString("tipo")),
            rs.getString("titulo"),
            rs.getString("mensaje"),
            rs.getString("referencia_id"),
            rs.getBoolean("leida"),
            instante(rs.getTimestamp("creada_en")),
            instante(rs.getTimestamp("leida_en")),
            rs.getString("trace_id"));

    private final JdbcTemplate jdbc;

    public NotificacionJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean guardarSiNoExiste(Notificacion n) {
        int filas = jdbc.update("INSERT INTO notificacion (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT DO NOTHING",
                n.id(), n.usuarioId(), n.eventoId(), n.tipo().name(), n.titulo(), n.mensaje(), n.referenciaId(),
                n.leida(), Timestamp.from(n.creadaEn()), marca(n.leidaEn()), n.traceId());
        return filas == 1;
    }

    @Override
    public Optional<Notificacion> buscarDelUsuario(UUID notificacionId, UUID usuarioId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM notificacion WHERE id = ? AND usuario_id = ?", MAPEO,
                notificacionId, usuarioId).stream().findFirst();
    }

    @Override
    public Optional<Notificacion> buscar(UUID notificacionId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM notificacion WHERE id = ?", MAPEO, notificacionId)
                .stream().findFirst();
    }

    @Override
    public void marcarLeida(UUID notificacionId, UUID usuarioId, Instant leidaEn) {
        jdbc.update("UPDATE notificacion SET leida = TRUE, leida_en = ? WHERE id = ? AND usuario_id = ? AND NOT leida",
                Timestamp.from(leidaEn), notificacionId, usuarioId);
    }

    @Override
    public int marcarTodasLeidas(UUID usuarioId, Instant leidaEn) {
        return jdbc.update("UPDATE notificacion SET leida = TRUE, leida_en = ? WHERE usuario_id = ? AND NOT leida",
                Timestamp.from(leidaEn), usuarioId);
    }

    @Override
    public PaginaNotificaciones listar(FiltroNotificaciones filtro) {
        StringBuilder where = new StringBuilder(" WHERE usuario_id = ?");
        List<Object> parametros = new ArrayList<>();
        parametros.add(filtro.usuarioId());
        if (filtro.tipo() != null) {
            where.append(" AND tipo = ?");
            parametros.add(filtro.tipo().name());
        }
        if (filtro.desde() != null) {
            where.append(" AND creada_en >= ?");
            parametros.add(Timestamp.from(filtro.desde()));
        }
        if (filtro.leida() != null) {
            where.append(" AND leida = ?");
            parametros.add(filtro.leida());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM notificacion" + where, Long.class,
                parametros.toArray());
        List<Object> conPagina = new ArrayList<>(parametros);
        conPagina.add(filtro.tamano());
        conPagina.add(filtro.desplazamiento());
        List<Notificacion> contenido = jdbc.query("SELECT " + COLUMNAS + " FROM notificacion" + where
                + " ORDER BY creada_en DESC, id DESC LIMIT ? OFFSET ?", MAPEO, conPagina.toArray());
        return new PaginaNotificaciones(contenido, filtro.pagina(), filtro.tamano(), total == null ? 0 : total);
    }

    private static Timestamp marca(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    private static Instant instante(Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }
}
