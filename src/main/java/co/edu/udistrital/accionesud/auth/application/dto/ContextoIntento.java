package co.edu.udistrital.accionesud.auth.application.dto;

public record ContextoIntento(String traceId, String ip, String userAgent) {

    public ContextoIntento {
        traceId = traceId == null ? "" : traceId;
        ip = ip == null ? "" : ip;
        userAgent = userAgent == null ? "" : userAgent;
    }
}
