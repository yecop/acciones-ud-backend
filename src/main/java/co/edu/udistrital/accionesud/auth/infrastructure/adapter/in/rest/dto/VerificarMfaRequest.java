package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerificarMfaRequest(
        @NotBlank String mfaToken,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "debe tener 6 dígitos") String codigo) {
}
