package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.auth.application.dto.ResultadoTokens;
import co.edu.udistrital.accionesud.auth.domain.model.Usuario;

import java.util.UUID;

public record TokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiraEn,
        UsuarioResponse usuario) {

    public static TokensResponse desde(ResultadoTokens resultado) {
        return new TokensResponse(
                resultado.accessToken(),
                resultado.refreshToken(),
                resultado.tokenType(),
                resultado.expiraEn(),
                UsuarioResponse.desde(resultado.usuario()));
    }

    public record UsuarioResponse(UUID id, String nombre, String rol) {

        public static UsuarioResponse desde(Usuario usuario) {
            return new UsuarioResponse(usuario.id(), usuario.nombre(), usuario.rol());
        }
    }
}
