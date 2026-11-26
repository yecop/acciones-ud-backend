package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Detalle legible de un error; campo se informa cuando aplica a una propiedad del request. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DetalleError(String campo, String problema) {

    public static DetalleError campo(String campo, String problema) {
        return new DetalleError(campo, problema);
    }

    public static DetalleError general(String problema) {
        return new DetalleError(null, problema);
    }
}
