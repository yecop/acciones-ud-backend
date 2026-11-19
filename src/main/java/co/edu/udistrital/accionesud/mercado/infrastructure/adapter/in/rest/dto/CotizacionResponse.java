package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto;

import java.time.Instant;

/**
 * Cotización servida desde la caché. Los montos viajan como texto con 2 decimales
 * (contrato), {@code actualizadoEn} siempre está presente, {@code umbralAntiguedadSegundos}
 * indica el máximo esperado para su mercado (15 s en EE. UU.) y {@code advertencia} solo
 * aparece si el dato está desactualizado o el proveedor no responde.
 */
public record CotizacionResponse(
        String ticker,
        String precio,
        String bid,
        String ask,
        String spread,
        long volumen,
        Instant actualizadoEn,
        String fuente,
        long antiguedadSegundos,
        long umbralAntiguedadSegundos,
        boolean desactualizada,
        AdvertenciaResponse advertencia) {
}
