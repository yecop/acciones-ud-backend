package co.edu.udistrital.accionesud.shared.domain.exception;

/** La función requiere una suscripción Premium activa (HTTP 403, código {@code FUNCION_PREMIUM}). */
public class FuncionPremiumRequeridaException extends AccesoDenegadoException {

    public static final String CODIGO = "FUNCION_PREMIUM";

    public FuncionPremiumRequeridaException() {
        super(CODIGO, "Esta función requiere una suscripción Premium activa");
    }
}
