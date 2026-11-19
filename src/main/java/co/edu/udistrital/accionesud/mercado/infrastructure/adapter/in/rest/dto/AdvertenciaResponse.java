package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto;

/**
 * Advertencia de un dato servido desde caché ({@code DATO_DESACTUALIZADO},
 * {@code PROVEEDOR_NO_DISPONIBLE} o {@code SIN_COTIZACION}).
 */
public record AdvertenciaResponse(String codigo, String mensaje) {
}
