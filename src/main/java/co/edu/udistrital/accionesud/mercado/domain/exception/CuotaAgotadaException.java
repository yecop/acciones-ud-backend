package co.edu.udistrital.accionesud.mercado.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/**
 * El limitador de cuota no autorizó la llamada al proveedor (se alcanzó el tope de diseño).
 * No significa que el proveedor esté caído: la caché conserva su último dato.
 */
public class CuotaAgotadaException extends DomainException {

    public static final String CODIGO = "CUOTA_PROVEEDOR_AGOTADA";

    public CuotaAgotadaException(String mensaje) {
        super(CODIGO, mensaje);
    }
}
