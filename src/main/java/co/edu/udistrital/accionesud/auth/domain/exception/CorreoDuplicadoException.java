package co.edu.udistrital.accionesud.auth.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.ConflictoException;

public class CorreoDuplicadoException extends ConflictoException {

    public CorreoDuplicadoException() {
        super("CORREO_DUPLICADO", "Ya existe una cuenta registrada con ese correo");
    }
}