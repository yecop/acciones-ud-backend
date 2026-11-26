package co.edu.udistrital.accionesud.shared.domain.exception;

import java.util.List;

/** Error de validación con detalle por campo. Se traduce a HTTP 400 VALIDACION_FALLIDA. */
public class ValidacionException extends DomainException {

    private final List<ErrorCampo> errores;

    public ValidacionException(List<ErrorCampo> errores) {
        super("VALIDACION_FALLIDA", "Uno o más campos no son válidos");
        this.errores = List.copyOf(errores);
    }

    public ValidacionException(String campo, String mensaje) {
        this(List.of(new ErrorCampo(campo, mensaje)));
    }

    public List<ErrorCampo> getErrores() {
        return errores;
    }

    public record ErrorCampo(String campo, String mensaje) {
    }
}
