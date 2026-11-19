package co.edu.udistrital.accionesud.mercado.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/**
 * El proveedor de datos de mercado no respondió (o respondió con error) y no existe
 * un dato previo en caché que se pueda servir. Se traduce a HTTP 502.
 */
public class ProveedorNoDisponibleException extends DomainException {

    public static final String CODIGO = "PROVEEDOR_NO_DISPONIBLE";

    public ProveedorNoDisponibleException(String mensaje) {
        super(CODIGO, mensaje);
    }

    public ProveedorNoDisponibleException(String mensaje, Throwable causa) {
        super(CODIGO, mensaje, causa);
    }
}
