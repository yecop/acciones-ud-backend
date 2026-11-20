package co.edu.udistrital.accionesud.perfil.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

public class UsuarioNoEncontradoException extends DomainException {

    public UsuarioNoEncontradoException() {
        super("USUARIO_NO_ENCONTRADO", "No se encontró el usuario autenticado");
    }
}