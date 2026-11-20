package co.edu.udistrital.accionesud.perfil.application.service;

import co.edu.udistrital.accionesud.perfil.application.dto.ActualizarPerfilCommand;
import co.edu.udistrital.accionesud.perfil.application.port.in.ActualizarPerfilUseCase;
import co.edu.udistrital.accionesud.perfil.application.port.in.ConsultarPerfilUseCase;
import co.edu.udistrital.accionesud.perfil.application.port.in.GestionarPreferenciasUseCase;
import co.edu.udistrital.accionesud.perfil.application.port.in.IdentificarUsuarioUseCase;
import co.edu.udistrital.accionesud.perfil.application.port.out.PerfilRepositoryPort;
import co.edu.udistrital.accionesud.perfil.domain.exception.PerfilNoEncontradoException;
import co.edu.udistrital.accionesud.perfil.domain.exception.UsuarioNoEncontradoException;
import co.edu.udistrital.accionesud.perfil.domain.model.PerfilUsuario;
import co.edu.udistrital.accionesud.perfil.domain.model.PreferenciasPerfil;

import java.util.Locale;
import java.util.UUID;

public class PerfilService implements IdentificarUsuarioUseCase, ConsultarPerfilUseCase,
        ActualizarPerfilUseCase, GestionarPreferenciasUseCase {

    private final PerfilRepositoryPort repositorio;

    public PerfilService(PerfilRepositoryPort repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public UUID identificar(String nombreAutenticado) {
        try {
            return UUID.fromString(nombreAutenticado);
        } catch (IllegalArgumentException e) {
            return repositorio.buscarIdPorCorreo(nombreAutenticado.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(UsuarioNoEncontradoException::new);
        }
    }

    @Override
    public PerfilUsuario consultarPerfil(UUID usuarioId) {
        return repositorio.buscarPerfil(usuarioId).orElseThrow(PerfilNoEncontradoException::new);
    }

    @Override
    public PerfilUsuario actualizarPerfil(UUID usuarioId, ActualizarPerfilCommand comando) {
        return repositorio.actualizarPerfil(usuarioId, comando)
                .orElseThrow(PerfilNoEncontradoException::new);
    }

    @Override
    public PreferenciasPerfil consultarPreferencias(UUID usuarioId) {
        exigirInversionista(usuarioId);
        return repositorio.buscarPreferencias(usuarioId).orElseGet(PreferenciasPerfil::porDefecto);
    }

    @Override
    public PreferenciasPerfil reemplazarPreferencias(UUID usuarioId, PreferenciasPerfil preferencias) {
        exigirInversionista(usuarioId);
        repositorio.guardarPreferencias(usuarioId, preferencias);
        return repositorio.buscarPreferencias(usuarioId).orElseThrow(PerfilNoEncontradoException::new);
    }

    private void exigirInversionista(UUID usuarioId) {
        if (!repositorio.existeInversionista(usuarioId)) {
            throw new PerfilNoEncontradoException();
        }
    }
}
