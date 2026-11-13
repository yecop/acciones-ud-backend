package co.edu.udistrital.accionesud.auth.domain.exception;

public class DemasiadasSolicitudesException extends AutenticacionException {

    private final long reintentarEnSegundos;

    public DemasiadasSolicitudesException(long reintentarEnSegundos) {
        super(CodigoAuth.DEMASIADAS_SOLICITUDES, "Límite de solicitudes superado");
        this.reintentarEnSegundos = Math.max(1, reintentarEnSegundos);
    }

    public long getReintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}
