package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.auth.application.dto.ResultadoRegistro;
import co.edu.udistrital.accionesud.auth.domain.model.EstadoUsuario;

import java.util.UUID;

public record RegistroResponse(UUID usuarioId, String correo, String estado, String mensaje) {

    public static RegistroResponse desde(ResultadoRegistro r) {
        String mensaje = r.estado() == EstadoUsuario.ACTIVO
                ? "Registro exitoso. Tu cuenta de corretaje fue creada."
                : "Recibimos tu registro, pero tu cuenta de corretaje está pendiente de alta. "
                  + "Se completará automáticamente en breve.";
        return new RegistroResponse(r.usuarioId(), r.correo(), r.estado().name(), mensaje);
    }
}