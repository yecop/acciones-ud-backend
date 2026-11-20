package co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.auth.domain.model.NivelRiesgo;
import co.edu.udistrital.accionesud.perfil.application.dto.ActualizarPerfilCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Actualización parcial del perfil. Los campos {@code null} se ignoran; enviar
 * una lista vacía en {@code mercados} o {@code sectores} elimina los intereses
 * existentes para esa categoría.
 */
public record ActualizarPerfilRequest(
        @Size(min = 1, max = 120)
        @Pattern(regexp = ".*\\S.*", message = "no puede estar vacío") String nombres,
        @Size(min = 1, max = 120)
        @Pattern(regexp = ".*\\S.*", message = "no puede estar vacío") String apellidos,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "formato de celular inválido") String celular,
        NivelRiesgo nivelRiesgo,
        @Size(max = 500) String objetivos,
        Set<@NotBlank String> mercados,
        Set<@NotBlank String> sectores) {

    public ActualizarPerfilCommand aComando() {
        return new ActualizarPerfilCommand(limpiar(nombres), limpiar(apellidos), celular,
                nivelRiesgo, limpiar(objetivos), mercados, sectores);
    }

    private static String limpiar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
