package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.auth.application.dto.ResultadoLogin;

public record LoginResponse(String mfaToken, String canal, long expiraEn) {

    public static LoginResponse desde(ResultadoLogin resultado) {
        return new LoginResponse(resultado.mfaToken(), resultado.canal(), resultado.expiraEn());
    }
}
