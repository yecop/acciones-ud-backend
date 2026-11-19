package co.edu.udistrital.accionesud.auth.application.dto;

import co.edu.udistrital.accionesud.auth.domain.model.Usuario;

public record ResultadoTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiraEn,
        Usuario usuario) {
}
