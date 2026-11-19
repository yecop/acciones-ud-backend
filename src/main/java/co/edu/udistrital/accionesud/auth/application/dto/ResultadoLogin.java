package co.edu.udistrital.accionesud.auth.application.dto;

public record ResultadoLogin(String mfaToken, String canal, long expiraEn) {
}
