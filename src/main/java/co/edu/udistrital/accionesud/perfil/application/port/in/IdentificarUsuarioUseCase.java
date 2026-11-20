package co.edu.udistrital.accionesud.perfil.application.port.in;

import java.util.UUID;

public interface IdentificarUsuarioUseCase {

    /** Convierte el nombre del usuario autenticado (UUID o correo) en el id del usuario. */
    UUID identificar(String nombreAutenticado);
}