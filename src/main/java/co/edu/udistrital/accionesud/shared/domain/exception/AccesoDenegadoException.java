package co.edu.udistrital.accionesud.shared.domain.exception;

/**
 * La operación no está permitida para el usuario autenticado. Se traduce a HTTP 403.
 *
 * <p>Las subclases fijan un código estable para el frontend (p. ej. {@code FUNCION_PREMIUM}).</p>
 */
public class AccesoDenegadoException extends DomainException {

    public static final String CODIGO = "ACCESO_DENEGADO";

    public AccesoDenegadoException(String mensaje) {
        this(CODIGO, mensaje);
    }

    protected AccesoDenegadoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
