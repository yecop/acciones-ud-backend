package co.edu.udistrital.accionesud.mercado.domain.exception;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

import java.util.Optional;

/**
 * El proveedor de datos de mercado no respondió (o respondió con error) y no existe
 * un dato previo en caché que se pueda servir. Se traduce a HTTP 502.
 *
 * <p>Cuando se conoce el mercado de la acción consultada, viaja en la excepción para que
 * la capa REST calcule cuándo puede haber datos nuevos ({@code Retry-After}), ya que cada
 * grupo de mercados se actualiza con un intervalo distinto.</p>
 */
public class ProveedorNoDisponibleException extends DomainException {

    public static final String CODIGO = "PROVEEDOR_NO_DISPONIBLE";

    private final CodigoMercado mercado;

    public ProveedorNoDisponibleException(String mensaje) {
        this(mensaje, (CodigoMercado) null);
    }

    public ProveedorNoDisponibleException(String mensaje, CodigoMercado mercado) {
        super(CODIGO, mensaje);
        this.mercado = mercado;
    }

    public ProveedorNoDisponibleException(String mensaje, Throwable causa) {
        super(CODIGO, mensaje, causa);
        this.mercado = null;
    }

    /** Mercado de la acción consultada, si se conoce. */
    public Optional<CodigoMercado> mercado() {
        return Optional.ofNullable(mercado);
    }
}
