package co.edu.udistrital.accionesud.auth.application.dto;

import java.time.Duration;
import java.util.Objects;

public record PoliticaAutenticacion(
        Duration mfaExpiracion,
        int mfaMaxIntentos,
        Duration mfaBloqueo,
        Duration reenvioEspera,
        int maxReenvios,
        Duration refreshExpiracion,
        int loginMaxFallos,
        Duration loginVentana) {

    public PoliticaAutenticacion {
        Objects.requireNonNull(mfaExpiracion, "mfaExpiracion");
        Objects.requireNonNull(mfaBloqueo, "mfaBloqueo");
        Objects.requireNonNull(reenvioEspera, "reenvioEspera");
        Objects.requireNonNull(refreshExpiracion, "refreshExpiracion");
        Objects.requireNonNull(loginVentana, "loginVentana");
    }
}
