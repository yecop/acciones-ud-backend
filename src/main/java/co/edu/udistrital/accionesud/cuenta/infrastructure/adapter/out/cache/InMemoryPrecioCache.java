package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.cache;

import co.edu.udistrital.accionesud.cuenta.application.port.out.PrecioCachePort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de salida: cache de precios de mercado en memoria.
 *
 * <p>Se precarga con algunos precios simulados. En caso de no encontrar un
 * activo, retorna {@link BigDecimal#ZERO} como respaldo seguro para que el
 * calculo de valoracion no falle; la estrategia puede evolucionar a consultar
 * un proveedor real.
 */
@Component
public class InMemoryPrecioCache implements PrecioCachePort {

    private final Map<String, BigDecimal> precios = new ConcurrentHashMap<>();

    public InMemoryPrecioCache() {
        // Precios de mercado simulados (seed).
        precios.put("AAPL", new BigDecimal("190.50"));
        precios.put("GOOGL", new BigDecimal("142.75"));
        precios.put("MSFT", new BigDecimal("415.20"));
        precios.put("TSLA", new BigDecimal("248.30"));
        precios.put("AMZN", new BigDecimal("178.90"));
    }

    @Override
    public BigDecimal obtenerPrecioActual(String activo) {
        if (activo == null) {
            return BigDecimal.ZERO;
        }
        return precios.getOrDefault(activo.trim().toUpperCase(), BigDecimal.ZERO);
    }

    /** Actualiza o inserta el precio de un activo en la cache. */
    public void actualizarPrecio(String activo, BigDecimal precio) {
        precios.put(activo.trim().toUpperCase(), precio);
    }
}
