package co.edu.udistrital.accionesud.perfil.application.port.in;

import co.edu.udistrital.accionesud.perfil.application.dto.ActualizarPerfilCommand;
import co.edu.udistrital.accionesud.perfil.domain.model.PerfilUsuario;

import java.util.UUID;

public interface ActualizarPerfilUseCase {

    PerfilUsuario actualizarPerfil(UUID usuarioId, ActualizarPerfilCommand comando);
}