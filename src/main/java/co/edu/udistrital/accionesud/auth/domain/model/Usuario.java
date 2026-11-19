package co.edu.udistrital.accionesud.auth.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Usuario(
        UUID id,
        String nombre,
        String correo,
        String contrasenaHash,
        String rol,
        boolean activo) {

    public Usuario {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(contrasenaHash, "contrasenaHash");
        Objects.requireNonNull(rol, "rol");
    }
}
