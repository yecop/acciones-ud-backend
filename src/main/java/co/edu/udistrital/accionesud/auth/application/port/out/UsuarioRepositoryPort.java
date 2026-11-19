package co.edu.udistrital.accionesud.auth.application.port.out;

import co.edu.udistrital.accionesud.auth.application.dto.NuevoUsuario;
import co.edu.udistrital.accionesud.auth.application.dto.SolicitudAlta;
import co.edu.udistrital.accionesud.auth.domain.model.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositoryPort {

    Optional<Usuario> buscarPorCorreo(String correo);

    Optional<Usuario> buscarPorId(UUID id);

    boolean existeCorreo(String correo);

    void registrarPendiente(NuevoUsuario nuevo);

    void marcarAltaCompleta(UUID usuarioId, String cuentaBroker);

    List<SolicitudAlta> buscarAltasPendientes(int limite);
}
