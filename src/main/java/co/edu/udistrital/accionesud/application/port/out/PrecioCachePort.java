package co.edu.udistrital.accionesud.application.port.out;

import java.math.BigDecimal;

/**
 * Puerto de salida: fuente de precios de mercado vigentes, servidos desde una
 * cache. En un entorno real estaria respaldada por un proveedor de cotizaciones
 * en tiempo real; aqui se implementa con una cache en memoria.
 */
public interface PrecioCachePort {

    /**
     * Precio de mercado vigente para un activo.
     *
     * @param activo ticker del activo (ej. "AAPL").
     * @return precio actual; si el activo no esta en cache, el adaptador decide
     *         una estrategia de respaldo (ej. usar el precio de compra).
     */
    BigDecimal obtenerPrecioActual(String activo);
}
