package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.util.UUID;

public final class TraceIdContext {

    private TraceIdContext() {
    }

    public static String actual() {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }

    public static String desde(HttpServletRequest request) {
        Object valor = request.getAttribute(TraceIdFilter.ATRIBUTO);
        if (valor instanceof String traceId) {
            return traceId;
        }
        return actual();
    }
}
