package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SimularOrdenRequest(
        @NotBlank @Size(max = 12) String ticker,
        @NotNull LadoOrden lado,
        @Min(1) int cantidad,
        UUID inversionistaId) {
}
