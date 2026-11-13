package co.edu.udistrital.accionesud.auth.domain.exception;

public class MfaInvalidoException extends AutenticacionException {

    public MfaInvalidoException() {
        super(CodigoAuth.MFA_INVALIDO, "Código de verificación inválido");
    }
}
