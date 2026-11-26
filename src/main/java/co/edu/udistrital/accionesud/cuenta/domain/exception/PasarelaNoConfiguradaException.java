package co.edu.udistrital.accionesud.cuenta.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/**
 * La recarga con pasarela no está habilitada en este ambiente (falta
 * {@code accionesud.integraciones.pagos=stripe} o sus claves). Responde 503
 * {@code PASARELA_NO_CONFIGURADA}.
 */
public class PasarelaNoConfiguradaException extends DomainException {

    public PasarelaNoConfiguradaException() {
        super("PASARELA_NO_CONFIGURADA", "La recarga con pasarela de pagos no está habilitada");
    }
}
