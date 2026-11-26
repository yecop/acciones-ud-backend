package co.edu.udistrital.accionesud.notificaciones.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/**
 * La notificación no existe o no pertenece al usuario (no se distingue para no revelar ids
 * ajenos). Responde 404 {@code NO_ENCONTRADO}, como espera el frontend.
 */
public class NotificacionNoEncontradaException extends DomainException {

    public NotificacionNoEncontradaException() {
        super("NO_ENCONTRADO", "La notificación no existe");
    }
}
