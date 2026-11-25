package co.edu.udistrital.accionesud.shared.domain.exception;

/**
 * El broker de órdenes no respondió o falló. El llamador decide si reintenta (con el mismo
 * idCliente, para no duplicar la orden).
 */
public class BrokerOrdenesNoDisponibleException extends DomainException {

    public static final String CODIGO = "PROVEEDOR_NO_DISPONIBLE";

    public BrokerOrdenesNoDisponibleException(String mensaje) {
        super(CODIGO, mensaje);
    }

    public BrokerOrdenesNoDisponibleException(String mensaje, Throwable causa) {
        super(CODIGO, mensaje, causa);
    }
}
