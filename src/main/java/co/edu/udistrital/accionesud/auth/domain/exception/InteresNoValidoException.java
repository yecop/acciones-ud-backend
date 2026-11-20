package co.edu.udistrital.accionesud.auth.domain.exception;

/** @deprecated Usar la excepción compartida para intereses no válidos. */
@Deprecated(forRemoval = false)
public class InteresNoValidoException
        extends co.edu.udistrital.accionesud.shared.domain.exception.InteresNoValidoException {

    public InteresNoValidoException(String tipo, String valor) {
        super(tipo, valor);
    }
}
