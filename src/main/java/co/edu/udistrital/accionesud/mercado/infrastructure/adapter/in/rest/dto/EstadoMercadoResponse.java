package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto;

import java.time.Instant;
import java.util.List;

/**
 * Representación REST del estado de un mercado en un instante dado.
 */
public record EstadoMercadoResponse(
        String codigo,
        String estado,
        boolean abierto,
        String zonaHoraria,
        Instant horaConsulta,
        Instant proximaApertura,
        Instant proximoCierre,
        List<RestriccionResponse> restricciones) {
}
