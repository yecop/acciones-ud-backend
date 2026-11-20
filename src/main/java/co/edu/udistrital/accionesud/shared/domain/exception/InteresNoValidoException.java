package co.edu.udistrital.accionesud.shared.domain.exception;

/** Indica que un mercado o sector de interés no existe en el catálogo. */
public class InteresNoValidoException extends DomainException {

    public InteresNoValidoException(String tipo, String valor) {
        super("INTERES_NO_VALIDO", "El " + tipo + " '" + valor + "' no existe");
    }
}
