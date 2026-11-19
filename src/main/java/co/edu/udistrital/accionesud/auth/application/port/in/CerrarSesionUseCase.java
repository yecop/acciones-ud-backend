package co.edu.udistrital.accionesud.auth.application.port.in;

import co.edu.udistrital.accionesud.auth.application.dto.ContextoIntento;

import java.util.UUID;

public interface CerrarSesionUseCase {

    void cerrarSesion(String refreshToken, UUID usuarioId, ContextoIntento contexto);
}
