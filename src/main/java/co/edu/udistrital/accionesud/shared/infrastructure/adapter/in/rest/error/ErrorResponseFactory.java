package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error;

import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.trace.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ErrorResponseFactory {

    public ErrorResponse crear(HttpServletRequest request, CodigoError codigo) {
        return crear(request, codigo.getStatus(), codigo.name(), codigo.getMensaje(), List.of(), Map.of());
    }

    public ErrorResponse crear(HttpServletRequest request, HttpStatus status, String codigo,
                               String mensaje, List<String> detalles, Map<String, Object> datos) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                codigo,
                mensaje,
                TraceIdContext.desde(request),
                request.getRequestURI(),
                detalles == null ? List.of() : List.copyOf(detalles),
                datos == null ? Map.of() : new LinkedHashMap<>(datos));
    }
}
