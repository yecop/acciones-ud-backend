package co.edu.udistrital.accionesud.mercado.domain.port.out;

import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de salida de la caché de cotizaciones por ticker (Redis en producción).
 */
public interface CotizacionCachePort {

    /** Guarda (o reemplaza) la cotización de su ticker. */
    void guardar(Cotizacion cotizacion);

    /** Guarda varias cotizaciones. */
    default void guardarTodas(Collection<Cotizacion> cotizaciones) {
        cotizaciones.forEach(this::guardar);
    }

    /** Última cotización conocida del ticker. */
    Optional<Cotizacion> obtener(String ticker);

    /** Últimas cotizaciones conocidas de varios tickers (solo los que existan en caché). */
    Map<String, Cotizacion> obtenerVarias(Collection<String> tickers);
}
