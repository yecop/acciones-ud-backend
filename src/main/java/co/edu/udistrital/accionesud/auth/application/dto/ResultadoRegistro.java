package co.edu.udistrital.accionesud.auth.application.dto;

import co.edu.udistrital.accionesud.auth.domain.model.EstadoUsuario;

import java.util.UUID;

public record ResultadoRegistro(UUID usuarioId, String correo, EstadoUsuario estado) {
}