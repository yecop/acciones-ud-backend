package co.edu.udistrital.accionesud.perfil.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PerfilUsuario(
        UUID usuarioId,
        String nombres,
        String apellidos,
        String correo,
        String celular,
        LocalDate fechaNacimiento,
        String estado,
        String nivelExperiencia,
        String nivelRiesgo,
        String objetivos,
        List<String> mercados,
        List<String> sectores) {
}