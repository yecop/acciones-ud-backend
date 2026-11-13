package co.edu.udistrital.accionesud.auth.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

public class InteresNoValidoException extends DomainException {

    public InteresNoValidoException(String tipo, String valor) {
        super("INTERES_NO_VALIDO", "El " + tipo + " '" + valor + "' no existe");
    }
}