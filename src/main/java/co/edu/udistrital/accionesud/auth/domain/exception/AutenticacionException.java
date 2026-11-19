package co.edu.udistrital.accionesud.auth.domain.exception;

public abstract class AutenticacionException extends RuntimeException {

    private final CodigoAuth codigo;

    protected AutenticacionException(CodigoAuth codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public CodigoAuth getCodigo() {
        return codigo;
    }
}
