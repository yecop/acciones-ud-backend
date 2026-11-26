package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia;

import co.edu.udistrital.accionesud.notificaciones.application.port.out.DestinatarioPort;
import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lee el titular de la cuenta ({@code cuenta_corretaje}) y las preferencias de SCRUM-22
 * ({@code preferencia_canal} y {@code preferencia_evento}) sin depender del módulo perfil.
 */
public class DestinatarioJdbcAdapter implements DestinatarioPort {

    private final JdbcTemplate jdbc;

    public DestinatarioJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<UUID> titularDeCuenta(UUID cuentaId) {
        return jdbc.query("SELECT inversionista_id FROM cuenta_corretaje WHERE id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), cuentaId).stream().findFirst();
    }

    @Override
    public Set<CanalNotificacion> canalesPara(UUID usuarioId, String eventoPreferencia) {
        Set<CanalNotificacion> canales = EnumSet.noneOf(CanalNotificacion.class);
        jdbc.query("SELECT pc.canal_id FROM preferencia_canal pc "
                        + "WHERE pc.inversionista_id = ? AND EXISTS ("
                        + "  SELECT 1 FROM preferencia_evento pe JOIN evento_notificacion en ON en.id = pe.evento_id"
                        + "  WHERE pe.inversionista_id = pc.inversionista_id AND en.nombre = ?)",
                rs -> {
                    canales.add(CanalNotificacion.desdeId(rs.getInt(1)));
                }, usuarioId, eventoPreferencia);
        return canales;
    }
}
