package co.edu.udistrital.accionesud.mercado.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/**
 * Se lanza cuando se consulta un mercado que no existe en el catálogo.
 */
public class MercadoNoEncontradoException extends DomainException {

    public MercadoNoEncontradoException(String codigo) {
        super("MERCADO_NO_ENCONTRADO", "No se encontró el mercado con código '" + codigo + "'");
    }
}
