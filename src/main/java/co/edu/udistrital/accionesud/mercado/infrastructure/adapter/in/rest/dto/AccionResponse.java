package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto;

/**
 * Acción del catálogo con su última cotización en caché (si existe).
 *
 * @param cotizacion  {@code null} (omitido en JSON) si aún no hay dato en caché.
 * @param advertencia {@code SIN_COTIZACION} cuando no hay cotización.
 */
public record AccionResponse(
        String ticker,
        String nombre,
        String mercado,
        String sector,
        String moneda,
        CotizacionResponse cotizacion,
        AdvertenciaResponse advertencia) {
}
