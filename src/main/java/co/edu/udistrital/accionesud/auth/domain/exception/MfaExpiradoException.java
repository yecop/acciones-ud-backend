package co.edu.udistrital.accionesud.auth.domain.exception;

public class MfaExpiradoException extends AutenticacionException {

    public MfaExpiradoException() {
        super(CodigoAuth.MFA_EXPIRADO, "El código de verificación venció");
    }
}
