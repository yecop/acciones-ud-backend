package co.edu.udistrital.accionesud.cuenta.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/** El retiro supera el saldo disponible. Responde 422 {@code FONDOS_INSUFICIENTES}. */
public class FondosInsuficientesException extends DomainException {

    public FondosInsuficientesException() {
        super("FONDOS_INSUFICIENTES", "El monto supera el saldo disponible de la cuenta");
    }
}
