package co.edu.udistrital.accionesud.perfil.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.perfil.application.dto.ActualizarPerfilCommand;
import co.edu.udistrital.accionesud.perfil.application.port.out.PerfilRepositoryPort;
import co.edu.udistrital.accionesud.perfil.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.perfil.domain.model.EventoNotificacion;
import co.edu.udistrital.accionesud.perfil.domain.model.OrdenPortafolio;
import co.edu.udistrital.accionesud.perfil.domain.model.PerfilUsuario;
import co.edu.udistrital.accionesud.perfil.domain.model.PreferenciasPerfil;
import co.edu.udistrital.accionesud.perfil.domain.model.TipoOrden;
import co.edu.udistrital.accionesud.perfil.domain.model.TipoVisualizacion;
import co.edu.udistrital.accionesud.shared.domain.exception.InteresNoValidoException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PerfilJdbcRepository implements PerfilRepositoryPort {

    private final JdbcTemplate jdbc;

    public PerfilJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> buscarIdPorCorreo(String correo) {
        List<UUID> ids = jdbc.query("SELECT id FROM usuario WHERE correo = ?",
                (rs, i) -> rs.getObject("id", UUID.class), correo);
        return ids.stream().findFirst();
    }

    @Override
    public boolean existeInversionista(UUID usuarioId) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM inversionista WHERE usuario_id = ?)", Boolean.class, usuarioId);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PerfilUsuario> buscarPerfil(UUID usuarioId) {
        List<String> mercados = jdbc.queryForList("""
                SELECT m.codigo FROM inversionista_interes_mercado im
                JOIN mercado m ON m.id = im.mercado_id
                WHERE im.inversionista_id = ? ORDER BY m.codigo""", String.class, usuarioId);
        List<String> sectores = jdbc.queryForList("""
                SELECT s.nombre FROM inversionista_interes_sector isc
                JOIN sector s ON s.id = isc.sector_id
                WHERE isc.inversionista_id = ? ORDER BY s.nombre""", String.class, usuarioId);

        List<PerfilUsuario> filas = jdbc.query("""
                SELECT u.id, u.nombre, u.apellido, u.correo, u.celular, u.fecha_nacimiento,
                       eu.nombre AS estado, ne.nombre AS nivel_experiencia,
                       i.nivel_riesgo, i.objetivos_financieros
                FROM usuario u
                JOIN estado_usuario eu ON eu.id = u.estado_usuario_id
                JOIN inversionista i ON i.usuario_id = u.id
                JOIN nivel_experiencia ne ON ne.id = i.nivel_experiencia_id
                WHERE u.id = ?""",
                (rs, i) -> new PerfilUsuario(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("correo"),
                        rs.getString("celular"),
                        rs.getObject("fecha_nacimiento", LocalDate.class),
                        rs.getString("estado"),
                        rs.getString("nivel_experiencia"),
                        rs.getString("nivel_riesgo"),
                        rs.getString("objetivos_financieros"),
                        mercados,
                        sectores),
                usuarioId);
        return filas.stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<PerfilUsuario> actualizarPerfil(UUID usuarioId, ActualizarPerfilCommand c) {
        if (buscarPerfil(usuarioId).isEmpty()) {
            return Optional.empty();
        }

        jdbc.update("""
                UPDATE usuario
                SET nombre = COALESCE(?::varchar, nombre),
                    apellido = COALESCE(?::varchar, apellido),
                    celular = COALESCE(?::varchar, celular)
                WHERE id = ?""",
                c.nombres(), c.apellidos(), c.celular(), usuarioId);

        String nivelRiesgo = c.nivelRiesgo() == null ? null : c.nivelRiesgo().name();
        jdbc.update("""
                UPDATE inversionista
                SET nivel_riesgo = COALESCE(?::varchar, nivel_riesgo),
                    objetivos_financieros = COALESCE(?::varchar, objetivos_financieros)
                WHERE usuario_id = ?""",
                nivelRiesgo, c.objetivos(), usuarioId);

        if (c.mercados() != null) {
            reemplazarMercados(usuarioId, c.mercados());
        }
        if (c.sectores() != null) {
            reemplazarSectores(usuarioId, c.sectores());
        }

        return buscarPerfil(usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PreferenciasPerfil> buscarPreferencias(UUID usuarioId) {
        Set<CanalNotificacion> canales = EnumSet.noneOf(CanalNotificacion.class);
        jdbc.queryForList("SELECT canal_id FROM preferencia_canal WHERE inversionista_id = ?",
                Integer.class, usuarioId).forEach(id -> canales.add(CanalNotificacion.desdeId(id)));
        Set<EventoNotificacion> eventos = EnumSet.noneOf(EventoNotificacion.class);
        jdbc.queryForList("SELECT evento_id FROM preferencia_evento WHERE inversionista_id = ?",
                Integer.class, usuarioId).forEach(id -> eventos.add(EventoNotificacion.desdeId(id)));

        List<PreferenciasPerfil> filas = jdbc.query("""
                SELECT tipo_visualizacion, orden_predeterminado, ocultar_activos_menor_peso,
                       tipo_orden_id, limite_operaciones_diarias, doble_autorizacion,
                       umbral_orden_grande, reporte_mensual
                FROM preferencia_perfil WHERE inversionista_id = ?""",
                (rs, i) -> new PreferenciasPerfil(
                        TipoVisualizacion.valueOf(rs.getString("tipo_visualizacion")),
                        OrdenPortafolio.valueOf(rs.getString("orden_predeterminado")),
                        rs.getBoolean("ocultar_activos_menor_peso"),
                        TipoOrden.desdeId(rs.getInt("tipo_orden_id")),
                        rs.getObject("limite_operaciones_diarias", Integer.class),
                        rs.getBoolean("doble_autorizacion"),
                        rs.getBigDecimal("umbral_orden_grande"),
                        rs.getBoolean("reporte_mensual"),
                        canales,
                        eventos),
                usuarioId);
        return filas.stream().findFirst();
    }

    @Override
    @Transactional
    public void guardarPreferencias(UUID usuarioId, PreferenciasPerfil p) {
        jdbc.update("""
                INSERT INTO preferencia_perfil (inversionista_id, tipo_visualizacion, orden_predeterminado,
                    ocultar_activos_menor_peso, tipo_orden_id, limite_operaciones_diarias,
                    doble_autorizacion, umbral_orden_grande, reporte_mensual)
                VALUES (?, ?, ?, ?, ?, ?::integer, ?, ?::numeric, ?)
                ON CONFLICT (inversionista_id) DO UPDATE SET
                    tipo_visualizacion = EXCLUDED.tipo_visualizacion,
                    orden_predeterminado = EXCLUDED.orden_predeterminado,
                    ocultar_activos_menor_peso = EXCLUDED.ocultar_activos_menor_peso,
                    tipo_orden_id = EXCLUDED.tipo_orden_id,
                    limite_operaciones_diarias = EXCLUDED.limite_operaciones_diarias,
                    doble_autorizacion = EXCLUDED.doble_autorizacion,
                    umbral_orden_grande = EXCLUDED.umbral_orden_grande,
                    reporte_mensual = EXCLUDED.reporte_mensual""",
                usuarioId, p.tipoVisualizacion().name(), p.ordenPredeterminado().name(),
                p.ocultarActivosMenorPeso(), p.tipoOrdenPredeterminado().id(),
                p.limiteOperacionesDiarias(), p.dobleAutorizacion(),
                p.umbralOrdenGrande(), p.reporteMensual());

        jdbc.update("DELETE FROM preferencia_canal WHERE inversionista_id = ?", usuarioId);
        for (CanalNotificacion canal : p.canales()) {
            jdbc.update("INSERT INTO preferencia_canal (inversionista_id, canal_id) VALUES (?, ?)",
                    usuarioId, canal.id());
        }
        jdbc.update("DELETE FROM preferencia_evento WHERE inversionista_id = ?", usuarioId);
        for (EventoNotificacion evento : p.eventos()) {
            jdbc.update("INSERT INTO preferencia_evento (inversionista_id, evento_id) VALUES (?, ?)",
                    usuarioId, evento.id());
        }
    }

    private void reemplazarMercados(UUID usuarioId, Set<String> mercados) {
        jdbc.update("DELETE FROM inversionista_interes_mercado WHERE inversionista_id = ?", usuarioId);
        for (String codigo : normalizar(mercados)) {
            int filas = jdbc.update("""
                    INSERT INTO inversionista_interes_mercado (inversionista_id, mercado_id)
                    SELECT ?, id FROM mercado WHERE lower(codigo) = ?""", usuarioId, codigo);
            if (filas == 0) {
                throw new InteresNoValidoException("mercado", codigo);
            }
        }
    }

    private void reemplazarSectores(UUID usuarioId, Set<String> sectores) {
        jdbc.update("DELETE FROM inversionista_interes_sector WHERE inversionista_id = ?", usuarioId);
        for (String sector : normalizar(sectores)) {
            int filas = jdbc.update("""
                    INSERT INTO inversionista_interes_sector (inversionista_id, sector_id)
                    SELECT ?, id FROM sector WHERE lower(nombre) = ?""", usuarioId, sector);
            if (filas == 0) {
                throw new InteresNoValidoException("sector", sector);
            }
        }
    }

    private static Set<String> normalizar(Set<String> valores) {
        return valores.stream()
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
