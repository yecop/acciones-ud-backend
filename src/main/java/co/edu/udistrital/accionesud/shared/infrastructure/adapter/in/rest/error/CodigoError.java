package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error;

import org.springframework.http.HttpStatus;

public enum CodigoError {

    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "La solicitud no es válida"),
    ERROR_VALIDACION(HttpStatus.BAD_REQUEST, "Uno o más campos no son válidos"),
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Se requiere autenticación"),
    ACCESO_DENEGADO(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta operación"),
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "No se encontró el recurso solicitado"),
    METODO_NO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED, "El método HTTP no está permitido"),
    TIPO_CONTENIDO_NO_SOPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido no es soportado"),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno");

    private final HttpStatus status;
    private final String mensaje;

    CodigoError(HttpStatus status, String mensaje) {
        this.status = status;
        this.mensaje = mensaje;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMensaje() {
        return mensaje;
    }

    public static CodigoError desdeStatus(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> SOLICITUD_INVALIDA;
            case 401 -> NO_AUTENTICADO;
            case 403 -> ACCESO_DENEGADO;
            case 404 -> RECURSO_NO_ENCONTRADO;
            case 405 -> METODO_NO_PERMITIDO;
            case 415 -> TIPO_CONTENIDO_NO_SOPORTADO;
            default -> status.is4xxClientError() ? SOLICITUD_INVALIDA : ERROR_INTERNO;
        };
    }
}
