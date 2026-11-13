package co.edu.udistrital.accionesud.shared.domain.exception;

/** Excepción de dominio que se traduce a HTTP 409. */
public abstract class ConflictoException extends DomainException {

    protected ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}