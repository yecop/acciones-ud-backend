package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String codigo,
        String mensaje,
        String traceId,
        String ruta,
        List<String> detalles,
        Map<String, Object> datos) {
}
