package co.edu.udistrital.accionesud.perfil.application.dto;

import co.edu.udistrital.accionesud.auth.domain.model.NivelRiesgo;

import java.util.Set;

public record ActualizarPerfilCommand(
        String nombres,
        String apellidos,
        String celular,
        NivelRiesgo nivelRiesgo,
        String objetivos,
        Set<String> mercados,
        Set<String> sectores) {
}