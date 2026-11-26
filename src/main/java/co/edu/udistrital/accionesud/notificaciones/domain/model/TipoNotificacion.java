package co.edu.udistrital.accionesud.notificaciones.domain.model;

import java.util.Optional;

/**
 * Tipos de notificación (SCRUM-51). El nombre es el valor que viaja en el contrato REST.
 *
 * <p>{@code eventoPreferencia} enlaza el tipo con el catálogo {@code evento_notificacion} (V11),
 * que es lo que el usuario elige en sus preferencias (SCRUM-22). Solo esos tipos se envían por
 * canales externos (SMS, correo, WhatsApp); el resto se muestra únicamente en la campana.</p>
 */
public enum TipoNotificacion {
    APERTURA_CIERRE_MERCADO("APERTURA_CIERRE_MERCADO"),
    RESUMEN_DIARIO_OPERACIONES("RESUMEN_DIARIO_OPERACIONES"),
    ORDEN_EJECUTADA("ORDEN_EJECUTADA"),
    ORDEN_CANCELADA(null),
    MOVIMIENTO_FONDOS(null),
    ALERTA_PRECIO(null),
    PROPUESTA_ORDENES(null),
    ASOCIACION_COMISIONISTA(null),
    SUSCRIPCION(null);

    private final String eventoPreferencia;

    TipoNotificacion(String eventoPreferencia) {
        this.eventoPreferencia = eventoPreferencia;
    }

    /** Nombre en {@code evento_notificacion} si el usuario puede suscribirse a canales externos. */
    public Optional<String> eventoPreferencia() {
        return Optional.ofNullable(eventoPreferencia);
    }
}
