package co.edu.udistrital.accionesud.mercado.domain.model;

/**
 * Fotografía del consumo de cuota de un proveedor de datos de mercado
 * (ventana principal: por minuto).
 *
 * @param proveedor                nombre del proveedor (ALPACA, ALPHA_VANTAGE, ...).
 * @param limiteProveedorPorMinuto límite oficial del plan del proveedor (llamadas/minuto).
 * @param topeDisenoPorMinuto      tope de diseño (60 % del límite por defecto).
 * @param llamadasUltimoMinuto     llamadas permitidas en la ventana móvil de 60 s.
 * @param llamadasTotales          llamadas permitidas desde el arranque.
 * @param llamadasRechazadas       intentos rechazados por superar algún tope.
 * @param alarmasEmitidas          número de alarmas de cuota registradas.
 */
public record EstadisticasCuota(String proveedor,
                                long limiteProveedorPorMinuto,
                                long topeDisenoPorMinuto,
                                long llamadasUltimoMinuto,
                                long llamadasTotales,
                                long llamadasRechazadas,
                                long alarmasEmitidas) {

    /** Porcentaje del límite del proveedor consumido en el último minuto. */
    public double porcentajeUsoProveedor() {
        return limiteProveedorPorMinuto == 0 ? 0 : (llamadasUltimoMinuto * 100.0) / limiteProveedorPorMinuto;
    }
}
