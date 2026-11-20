package co.edu.udistrital.accionesud.perfil.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

public class PerfilNoEncontradoException extends DomainException {

    public PerfilNoEncontradoException() {
        super("PERFIL_NO_ENCONTRADO", "No se encontró el perfil de inversionista");
    }
}