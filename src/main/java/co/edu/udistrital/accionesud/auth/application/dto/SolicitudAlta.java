package co.edu.udistrital.accionesud.auth.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SolicitudAlta(
        UUID usuarioId,
        String nombres,
        String apellidos,
        String correo,
        String celular,
        LocalDate fechaNacimiento) {
}