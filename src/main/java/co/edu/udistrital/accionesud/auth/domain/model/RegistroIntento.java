package co.edu.udistrital.accionesud.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RegistroIntento(
        UUID usuarioId,
        String correo,
        TipoIntento tipo,
        ResultadoIntento resultado,
        String motivo,
        String ip,
        String userAgent,
        String traceId,
        Instant creadoEn) {
}
