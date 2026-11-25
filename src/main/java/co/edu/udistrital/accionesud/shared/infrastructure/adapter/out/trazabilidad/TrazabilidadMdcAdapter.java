package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.trazabilidad;

import co.edu.udistrital.accionesud.shared.application.port.out.TrazabilidadPort;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.trace.TraceIdContext;

/**
 * Lee el traceId que {@code TraceIdFilter} deja en el MDC durante cada solicitud HTTP.
 * Fuera de una solicitud (tareas programadas) genera uno nuevo.
 */
public class TrazabilidadMdcAdapter implements TrazabilidadPort {

    @Override
    public String traceIdActual() {
        return TraceIdContext.actual();
    }
}
