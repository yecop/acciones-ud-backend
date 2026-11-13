package co.edu.udistrital.accionesud.auth.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

public class BrokerNoDisponibleException extends DomainException {

    public BrokerNoDisponibleException(String motivo, Throwable causa) {
        super("BROKER_NO_DISPONIBLE", motivo, causa);
    }
}