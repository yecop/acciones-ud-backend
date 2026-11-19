package co.edu.udistrital.accionesud.mercado.domain.port.out;

import co.edu.udistrital.accionesud.mercado.domain.exception.CuotaAgotadaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;

import java.util.List;
import java.util.Set;

/**
 * Puerto de salida hacia una fuente de cotizaciones (SCRUM-24).
 *
 * <p>Regla de negocio: los usuarios nunca consultan a los proveedores. Solo el
 * actualizador programado del backend invoca este puerto y deja el resultado en la
 * caché ({@link CotizacionCachePort}); los endpoints leen siempre de la caché.</p>
 *
 * <p>Implementaciones:</p>
 * <ul>
 *   <li>Alpaca (NYSE, NASDAQ): una consulta agrupada con todos los tickers.</li>
 *   <li>Internacional (LSE, TSE, ASX): Alpha Vantage con respaldo Finnhub y Polygon,
 *   un ticker por consulta.</li>
 * </ul>
 * Cada implementación aplica su propio limitador de cuota antes de cada llamada externa.
 */
public interface DatosMercadoPort {

    /** Identificador de la fuente (para logs y estado del proveedor). */
    String fuente();

    /** Mercados cuyas acciones cotiza esta fuente. */
    Set<CodigoMercado> mercadosSoportados();

    /** Cuántos tickers admite una sola consulta (Alpaca: todos; internacionales: 1). */
    default int maxTickersPorConsulta() {
        return Integer.MAX_VALUE;
    }

    /**
     * Consulta las cotizaciones de los tickers indicados.
     *
     * @param tickers símbolos a consultar (como máximo {@link #maxTickersPorConsulta()}).
     * @return cotizaciones recibidas (puede faltar alguna si el proveedor no la reporta).
     * @throws ProveedorNoDisponibleException si el proveedor falla o no está configurado.
     * @throws CuotaAgotadaException si el limitador de cuota no autorizó la llamada.
     */
    List<Cotizacion> consultarCotizaciones(Set<String> tickers);
}
