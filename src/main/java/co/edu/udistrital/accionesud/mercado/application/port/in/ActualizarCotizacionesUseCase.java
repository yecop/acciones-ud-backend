package co.edu.udistrital.accionesud.mercado.application.port.in;

import java.util.Map;

/**
 * Caso de uso del actualizador programado. Cada ciclo consulta a las fuentes del grupo
 * (una consulta por fuente) y escribe el resultado en la caché.
 */
public interface ActualizarCotizacionesUseCase {

    /** NYSE y NASDAQ (Alpaca, una consulta agrupada cada 5 s con el mercado abierto). */
    Map<String, ResultadoActualizacion> actualizarEstadosUnidos();

    /** LSE, TSE y ASX (Alpha Vantage → Finnhub → Polygon, pocos tickers por ciclo). */
    Map<String, ResultadoActualizacion> actualizarInternacionales();

    enum ResultadoActualizacion {
        /** Se consultó al proveedor y se escribió la caché. */
        ACTUALIZADO,
        /** Mercados cerrados y la caché ya tiene datos: no se gasta cuota. */
        OMITIDO_MERCADO_CERRADO,
        /** El limitador de cuota rechazó la llamada. */
        OMITIDO_CUOTA_AGOTADA,
        /** El proveedor falló; la caché conserva el último dato. */
        FALLA_PROVEEDOR
    }
}
