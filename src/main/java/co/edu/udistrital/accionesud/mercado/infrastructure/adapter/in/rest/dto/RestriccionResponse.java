package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto;

/**
 * Representación REST de una restricción vigente (p. ej. feriado).
 */
public record RestriccionResponse(
        String codigo,
        String descripcion) {
}
