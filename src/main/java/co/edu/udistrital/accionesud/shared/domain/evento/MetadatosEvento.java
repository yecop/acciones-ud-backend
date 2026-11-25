package co.edu.udistrital.accionesud.shared.domain.evento;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Datos comunes a todo evento de dominio (SCRUM-33).
 *
 * @param id         identificador único del evento (permite a los consumidores descartar duplicados).
 * @param ocurridoEn instante en que ocurrió el hecho.
 * @param traceId    traceId de la solicitud que originó el evento.
 * @param actorId    usuario que provocó el hecho; {@code null} si lo originó el sistema.
 */
public record MetadatosEvento(UUID id, Instant ocurridoEn, String traceId, UUID actorId) {

    public MetadatosEvento {
        Objects.requireNonNull(id, "El id del evento es obligatorio");
        Objects.requireNonNull(ocurridoEn, "La fecha del evento es obligatoria");
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("El traceId del evento es obligatorio");
        }
    }

    public static MetadatosEvento nuevo(Instant ocurridoEn, String traceId, UUID actorId) {
        return new MetadatosEvento(UUID.randomUUID(), ocurridoEn, traceId, actorId);
    }
}
