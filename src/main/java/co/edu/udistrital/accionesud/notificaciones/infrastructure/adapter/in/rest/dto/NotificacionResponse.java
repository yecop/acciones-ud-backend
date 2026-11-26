package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/** Forma acordada con el frontend ({@code src/api/notificacionesApi.js}); {@code referenciaId} puede ser null. */
public record NotificacionResponse(UUID id, String tipo, String titulo, String mensaje, boolean leida,
                                   Instant creadaEn,
                                   @JsonInclude(JsonInclude.Include.ALWAYS) String referenciaId) {

    public static NotificacionResponse desde(Notificacion n) {
        return new NotificacionResponse(n.id(), n.tipo().name(), n.titulo(), n.mensaje(), n.leida(), n.creadaEn(),
                n.referenciaId());
    }
}
