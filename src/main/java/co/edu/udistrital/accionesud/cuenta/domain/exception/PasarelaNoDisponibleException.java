package co.edu.udistrital.accionesud.cuenta.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/** La pasarela de pagos no respondió o rechazó la solicitud. Responde 502 {@code PASARELA_NO_DISPONIBLE}. */
public class PasarelaNoDisponibleException extends DomainException {

    public PasarelaNoDisponibleException(String mensaje) {
        super("PASARELA_NO_DISPONIBLE", mensaje);
    }

    public PasarelaNoDisponibleException(String mensaje, Throwable causa) {
        super("PASARELA_NO_DISPONIBLE", mensaje, causa);
    }
}
