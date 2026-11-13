package co.edu.udistrital.accionesud.auth.domain.exception;

public class CredencialesInvalidasException extends AutenticacionException {

    public CredencialesInvalidasException() {
        super(CodigoAuth.CREDENCIALES_INVALIDAS, "Credenciales inválidas");
    }
}
