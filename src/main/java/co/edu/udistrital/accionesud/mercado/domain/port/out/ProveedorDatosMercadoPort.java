package co.edu.udistrital.accionesud.mercado.domain.port.out;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;

import java.util.Optional;
import java.util.Set;

/**
 * Puerto de salida (Strategy) para integrar distintos proveedores de datos de mercado.
 *
 * <p>Cada implementación representa una estrategia concreta:
 * <ul>
 *     <li>Alpaca → NYSE, NASDAQ</li>
 *     <li>Alpha Vantage (recomendado), Finnhub o Polygon.io → LSE, TSE, ASX</li>
 * </ul>
 * El cálculo de estado se realiza en el dominio a partir de la hora inyectada por
 * {@link RelojPort}; este puerto permite además consultar un posible estado reportado
 * directamente por el proveedor (p. ej. cierres no planeados).</p>
 */
public interface ProveedorDatosMercadoPort {

    /** Nombre identificador del proveedor (p. ej. "ALPACA", "ALPHA_VANTAGE"). */
    String nombre();

    /** Conjunto de mercados que esta estrategia es capaz de atender. */
    Set<CodigoMercado> mercadosSoportados();

    default boolean soporta(CodigoMercado codigo) {
        return mercadosSoportados().contains(codigo);
    }

    /**
     * Consulta opcional al proveedor sobre un estado forzado (cierre no planeado,
     * halt, etc.). Un {@link Optional#empty()} indica que el estado se debe derivar
     * del horario calculado por el dominio.
     */
    Optional<EstadoReportado> consultarEstadoReportado(CodigoMercado codigo);

    /**
     * Estado reportado por un proveedor externo cuando difiere del horario regular.
     */
    record EstadoReportado(boolean abierto, String motivo) {
    }
}
