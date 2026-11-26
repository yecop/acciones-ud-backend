package co.edu.udistrital.accionesud.notificaciones.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/** Un canal externo no pudo entregar la notificación; el envío se reintenta más tarde. */
public class EnvioFallidoException extends DomainException {

    public EnvioFallidoException(String mensaje) {
        super("ENVIO_FALLIDO", mensaje);
    }
}
