package co.edu.udistrital.accionesud.mercado.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/**
 * Se consulta un ticker que no existe en el catálogo de acciones. Se traduce a HTTP 404.
 */
public class AccionNoEncontradaException extends DomainException {

    public static final String CODIGO = "ACCION_NO_ENCONTRADA";

    public AccionNoEncontradaException(String ticker) {
        super(CODIGO, "No se encontró la acción con ticker '" + ticker + "'");
    }
}
