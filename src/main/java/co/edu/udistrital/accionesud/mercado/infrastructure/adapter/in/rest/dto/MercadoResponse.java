package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto;

/**
 * Representación REST de un mercado y su horario de operación.
 */
public record MercadoResponse(
        String codigo,
        String nombre,
        String zonaHoraria,
        String horaApertura,
        String horaCierre,
        String diasHabiles) {
}
