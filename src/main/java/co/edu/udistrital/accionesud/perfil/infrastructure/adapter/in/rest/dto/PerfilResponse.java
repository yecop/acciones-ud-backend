package co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.perfil.domain.model.PerfilUsuario;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PerfilResponse(
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

    public static PerfilResponse desde(PerfilUsuario p) {
        return new PerfilResponse(p.usuarioId(), p.nombres(), p.apellidos(), p.correo(), p.celular(),
                p.fechaNacimiento(), p.estado(), p.nivelExperiencia(), p.nivelRiesgo(), p.objetivos(),
                p.mercados(), p.sectores());
    }
}