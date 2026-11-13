package co.edu.udistrital.accionesud.auth.application.port.out;

import co.edu.udistrital.accionesud.auth.domain.model.Usuario;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositoryPort {

    Optional<Usuario> buscarPorCorreo(String correo);

    Optional<Usuario> buscarPorId(UUID id);
}
