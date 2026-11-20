package co.edu.udistrital.accionesud.perfil.application.port.out;

import co.edu.udistrital.accionesud.perfil.application.dto.ActualizarPerfilCommand;
import co.edu.udistrital.accionesud.perfil.domain.model.PerfilUsuario;
import co.edu.udistrital.accionesud.perfil.domain.model.PreferenciasPerfil;

import java.util.Optional;
import java.util.UUID;

public interface PerfilRepositoryPort {

    Optional<UUID> buscarIdPorCorreo(String correo);

    boolean existeInversionista(UUID usuarioId);

    Optional<PerfilUsuario> buscarPerfil(UUID usuarioId);

    void actualizarPerfil(UUID usuarioId, ActualizarPerfilCommand comando);

    Optional<PreferenciasPerfil> buscarPreferencias(UUID usuarioId);

    void guardarPreferencias(UUID usuarioId, PreferenciasPerfil preferencias);
}