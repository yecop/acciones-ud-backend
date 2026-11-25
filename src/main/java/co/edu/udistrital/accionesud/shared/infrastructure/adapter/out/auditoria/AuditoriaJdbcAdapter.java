package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.auditoria;

import co.edu.udistrital.accionesud.shared.application.port.out.RegistroAuditoriaPort;
import co.edu.udistrital.accionesud.shared.domain.auditoria.RegistroAuditoria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Persiste la auditoría en {@code auditoria_evento} (V12). Usa el mismo {@link JdbcTemplate}
 * que el resto de módulos, así que dentro de una transacción el registro se confirma o se
 * deshace junto con el cambio de negocio.
 */
public class AuditoriaJdbcAdapter implements RegistroAuditoriaPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaJdbcAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public void registrar(RegistroAuditoria r) {
        jdbc.update("""
                INSERT INTO auditoria_evento (
                    ocurrido_en, actor_id, accion, entidad, entidad_id, resultado, detalle, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """,
                Timestamp.from(r.ocurridoEn()), r.actorId(), r.accion(), r.entidad(), r.entidadId(),
                r.resultado().name(), aJson(r), r.traceId());
    }

    private String aJson(RegistroAuditoria r) {
        try {
            return json.writeValueAsString(r.detalle());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el detalle de auditoría", e);
        }
    }
}
