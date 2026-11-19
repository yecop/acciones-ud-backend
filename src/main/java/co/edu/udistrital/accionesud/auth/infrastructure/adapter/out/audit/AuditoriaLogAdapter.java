package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.audit;

import co.edu.udistrital.accionesud.auth.application.port.out.AuditoriaPort;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.trace.TraceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class AuditoriaLogAdapter implements AuditoriaPort {

    private static final Logger AUDITORIA = LoggerFactory.getLogger("AUDITORIA");

    @Override
    public void registrar(String evento, UUID usuarioId, Map<String, Object> datos) {
        AUDITORIA.info("evento={} usuarioId={} traceId={} datos={}",
                evento, usuarioId, TraceIdContext.actual(), datos);
    }
}