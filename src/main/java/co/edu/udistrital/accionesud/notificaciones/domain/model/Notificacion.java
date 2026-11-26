package co.edu.udistrital.accionesud.notificaciones.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Notificación de un usuario (SCRUM-51). Se crea a partir de un evento de dominio y es única
 * por (usuario, evento, tipo): procesar el mismo evento otra vez no la duplica.
 *
 * @param referenciaId entidad que la originó (orden, propuesta, alerta…) o {@code null}.
 */
public record Notificacion(UUID id,
                           UUID usuarioId,
                           UUID eventoId,
                           TipoNotificacion tipo,
                           String titulo,
                           String mensaje,
                           String referenciaId,
                           boolean leida,
                           Instant creadaEn,
                           Instant leidaEn,
                           String traceId) {

    public static final int MAX_TITULO = 120;
    public static final int MAX_MENSAJE = 500;

    public Notificacion {
        Objects.requireNonNull(id, "El id es obligatorio");
        Objects.requireNonNull(usuarioId, "El usuario es obligatorio");
        Objects.requireNonNull(eventoId, "El evento es obligatorio");
        Objects.requireNonNull(tipo, "El tipo es obligatorio");
        Objects.requireNonNull(creadaEn, "La fecha es obligatoria");
        titulo = recortar(Objects.requireNonNull(titulo, "El título es obligatorio"), MAX_TITULO);
        mensaje = recortar(Objects.requireNonNull(mensaje, "El mensaje es obligatorio"), MAX_MENSAJE);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("El traceId es obligatorio");
        }
        if (leida != (leidaEn != null)) {
            throw new IllegalArgumentException("leidaEn debe existir solo si la notificación está leída");
        }
    }

    /**
     * Crea una notificación nueva sin leer. El id se deriva de (evento, usuario, tipo), así que
     * reprocesar el mismo evento produce exactamente el mismo id.
     */
    public static Notificacion nueva(UUID usuarioId, UUID eventoId, TipoNotificacion tipo, String titulo,
                                     String mensaje, String referenciaId, Instant creadaEn, String traceId) {
        UUID id = UUID.nameUUIDFromBytes((eventoId + "|" + usuarioId + "|" + tipo.name())
                .getBytes(StandardCharsets.UTF_8));
        return new Notificacion(id, usuarioId, eventoId, tipo, titulo, mensaje, referenciaId, false,
                creadaEn, null, traceId);
    }

    /** Idempotente: si ya estaba leída, conserva la fecha original. */
    public Notificacion marcarLeida(Instant ahora) {
        if (leida) {
            return this;
        }
        return new Notificacion(id, usuarioId, eventoId, tipo, titulo, mensaje, referenciaId, true,
                creadaEn, Objects.requireNonNull(ahora), traceId);
    }

    private static String recortar(String texto, int maximo) {
        String limpio = texto.strip();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo - 1) + "…";
    }
}
