package co.edu.udistrital.accionesud.auth.application.dto;

import co.edu.udistrital.accionesud.auth.domain.model.NivelExperiencia;
import co.edu.udistrital.accionesud.auth.domain.model.NivelRiesgo;

import java.time.LocalDate;
import java.util.Set;

public record RegistrarUsuarioCommand(
        String nombres,
        String apellidos,
        String correo,
        String contrasena,
        String celular,
        LocalDate fechaNacimiento,
        NivelExperiencia nivelExperiencia,
        NivelRiesgo nivelRiesgo,
        String objetivos,
        Set<String> mercados,
        Set<String> sectores) {

    @Override
    public String toString() {
        return "RegistrarUsuarioCommand[***]";
    }
}