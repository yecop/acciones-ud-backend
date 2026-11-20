package co.edu.udistrital.accionesud.perfil.application.port.in;

import co.edu.udistrital.accionesud.perfil.domain.model.PreferenciasPerfil;

import java.util.UUID;

public interface GestionarPreferenciasUseCase {

    PreferenciasPerfil consultarPreferencias(UUID usuarioId);

    PreferenciasPerfil reemplazarPreferencias(UUID usuarioId, PreferenciasPerfil preferencias);
}