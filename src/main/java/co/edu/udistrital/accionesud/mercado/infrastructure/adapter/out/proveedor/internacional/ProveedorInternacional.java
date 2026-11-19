package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional;

import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort.EstadoReportado;

import java.time.Instant;
import java.util.Optional;

/**
 * Contrato interno para los proveedores de mercados internacionales (LSE, TSE, ASX).
 *
 * <p>Cada implementación (Alpha Vantage, Finnhub, Polygon) encapsula su llamada
 * HTTP. El orquestador {@code ProveedorInternacionalConRespaldo} las encadena por
 * prioridad para aplicar failover.</p>
 */
public interface ProveedorInternacional {

    /** Nombre del proveedor, para trazabilidad. */
    String nombre();

    /** Indica si el proveedor tiene credenciales para operar. */
    boolean estaDisponible();

    /**
     * Consulta el estado del mercado dado.
     *
     * @return el estado reportado, o vacío si el proveedor no pudo responder.
     */
    Optional<EstadoReportado> consultar(CodigoMercado codigo);

    /**
     * Cotiza un símbolo (SCRUM-24). Usa la API sin la caché TTL del cliente HTTP; el
     * limitador de cuota lo aplica quien invoca ({@code AdaptadorInternacionalDatos}).
     *
     * @param simbolo    símbolo en el formato del proveedor (ticker + sufijo de la bolsa).
     * @param ticker     ticker de la plataforma con el que se guarda la cotización.
     * @param recibidoEn instante con el que se marca {@code actualizadoEn}.
     * @return la cotización, o vacío si el proveedor no tiene un dato utilizable.
     */
    Optional<Cotizacion> cotizar(String simbolo, String ticker, Instant recibidoEn);
}
