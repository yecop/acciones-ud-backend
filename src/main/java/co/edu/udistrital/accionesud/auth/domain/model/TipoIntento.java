package co.edu.udistrital.accionesud.auth.domain.model;

public enum TipoIntento {
    LOGIN,
    MFA_ENVIO,
    MFA_VERIFICACION,
    MFA_REENVIO,
    REFRESH,
    LOGOUT
}
