package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record ReenviarMfaRequest(@NotBlank String mfaToken) {
}
