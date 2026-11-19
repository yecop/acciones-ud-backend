package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache;

import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.port.out.CotizacionCachePort;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché de cotizaciones en memoria. Se usa con
 * {@code accionesud.mercados.cotizaciones.cache.tipo=memoria} (pruebas o desarrollo sin
 * Redis) y como respaldo local del adaptador de Redis.
 *
 * <p>Sin dependencias de Spring: el bean se registra en {@code CotizacionesBeansConfig}.</p>
 */
public class InMemoryCotizacionCacheAdapter implements CotizacionCachePort {

    private final Map<String, Cotizacion> cotizaciones = new ConcurrentHashMap<>();

    @Override
    public void guardar(Cotizacion cotizacion) {
        cotizaciones.put(cotizacion.ticker(), cotizacion);
    }

    @Override
    public Optional<Cotizacion> obtener(String ticker) {
        if (ticker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cotizaciones.get(ticker.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public Map<String, Cotizacion> obtenerVarias(Collection<String> tickers) {
        Map<String, Cotizacion> resultado = new LinkedHashMap<>();
        for (String ticker : tickers) {
            obtener(ticker).ifPresent(c -> resultado.put(c.ticker(), c));
        }
        return resultado;
    }

    /** Vacía la caché (pruebas). */
    public void limpiar() {
        cotizaciones.clear();
    }
}
