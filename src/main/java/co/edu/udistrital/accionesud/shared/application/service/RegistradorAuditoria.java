package co.edu.udistrital.accionesud.shared.application.service;

import co.edu.udistrital.accionesud.shared.application.port.out.RegistroAuditoriaPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TrazabilidadPort;
import co.edu.udistrital.accionesud.shared.domain.auditoria.RegistroAuditoria;
import co.edu.udistrital.accionesud.shared.domain.auditoria.ResultadoAuditoria;
import co.edu.udistrital.accionesud.shared.domain.evento.MetadatosEvento;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Atajo para los casos de uso: arma el {@link RegistroAuditoria} con la hora del reloj y el
 * traceId de la solicitud en curso, y crea los metadatos de los eventos con el mismo traceId
 * (SCRUM-33). Así cada módulo no repite esa lógica ni se olvida del traceId.
 */
public class RegistradorAuditoria {

    private final RegistroAuditoriaPort auditoria;
    private final TrazabilidadPort trazabilidad;
    private final Clock reloj;

    public RegistradorAuditoria(RegistroAuditoriaPort auditoria, TrazabilidadPort trazabilidad, Clock reloj) {
        this.auditoria = Objects.requireNonNull(auditoria);
        this.trazabilidad = Objects.requireNonNull(trazabilidad);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public void registrar(UUID actorId, String accion, String entidad, String entidadId,
                          ResultadoAuditoria resultado, Map<String, String> detalle) {
        auditoria.registrar(new RegistroAuditoria(reloj.instant(), actorId, accion, entidad, entidadId,
                resultado, detalle, trazabilidad.traceIdActual()));
    }

    public void exitoso(UUID actorId, String accion, String entidad, String entidadId, Map<String, String> detalle) {
        registrar(actorId, accion, entidad, entidadId, ResultadoAuditoria.EXITOSO, detalle);
    }

    /** Metadatos para un evento con la hora actual y el traceId de la solicitud. */
    public MetadatosEvento metadatosEvento(UUID actorId) {
        return MetadatosEvento.nuevo(reloj.instant(), trazabilidad.traceIdActual(), actorId);
    }
}
