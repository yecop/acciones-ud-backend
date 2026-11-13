package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.auth.application.dto.RegistrarUsuarioCommand;
import co.edu.udistrital.accionesud.auth.domain.model.NivelExperiencia;
import co.edu.udistrital.accionesud.auth.domain.model.NivelRiesgo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record RegistroRequest(
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 120) String apellidos,
        @NotBlank @Email @Size(max = 160) String correo,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "debe incluir al menos una letra y un número")
        String contrasena,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "formato de celular inválido") String celular,
        @NotNull @Past LocalDate fechaNacimiento,
        @NotNull NivelExperiencia nivelExperiencia,
        @NotNull NivelRiesgo nivelRiesgo,
        @Size(max = 500) String objetivos,
        @NotEmpty Set<@NotBlank String> mercados,
        @NotEmpty Set<@NotBlank String> sectores) {

    @JsonIgnore
    @AssertTrue(message = "debe ser mayor de 18 años")
    public boolean isMayorDeEdad() {
        return fechaNacimiento == null || !fechaNacimiento.isAfter(LocalDate.now().minusYears(18));
    }

    public RegistrarUsuarioCommand aComando() {
        return new RegistrarUsuarioCommand(nombres, apellidos, correo, contrasena, celular,
                fechaNacimiento, nivelExperiencia, nivelRiesgo, objetivos, mercados, sectores);
    }

    @Override
    public String toString() {
        return "RegistroRequest[***]";
    }
}