package co.edu.udistrital.accionesud.cuenta.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/** El usuario no tiene cuenta de corretaje (p. ej. un comisionista). Responde 404 {@code NO_ENCONTRADO}. */
public class CuentaNoEncontradaException extends DomainException {

    public CuentaNoEncontradaException() {
        super("NO_ENCONTRADO", "El usuario no tiene una cuenta de corretaje");
    }
}
