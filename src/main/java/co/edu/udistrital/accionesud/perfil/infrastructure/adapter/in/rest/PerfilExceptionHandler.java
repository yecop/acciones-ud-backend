package co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error.CodigoError;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error.ErrorResponse;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error.ErrorResponseFactory;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/** Traduce enums inválidos de los DTO de perfil sin cambiar el contrato de otros módulos. */
@RestControllerAdvice(assignableTypes = PerfilController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PerfilExceptionHandler {

    private final ErrorResponseFactory factory;

    public PerfilExceptionHandler(ErrorResponseFactory factory) {
        this.factory = factory;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoIlegible(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        if (ex.getCause() instanceof InvalidFormatException formato && !formato.getPath().isEmpty()) {
            String campo = rutaCampo(formato);
            String mensaje = "valor no permitido";
            ErrorResponse respuesta = factory.crear(request, HttpStatus.BAD_REQUEST,
                    CodigoError.ERROR_VALIDACION.name(), CodigoError.ERROR_VALIDACION.getMensaje(),
                    List.of(campo + ": " + mensaje),
                    Map.of("campos", List.of(Map.of("campo", campo, "mensaje", mensaje))));
            return ResponseEntity.badRequest().body(respuesta);
        }

        ErrorResponse respuesta = factory.crear(request, CodigoError.SOLICITUD_INVALIDA);
        return ResponseEntity.badRequest().body(respuesta);
    }

    private String rutaCampo(InvalidFormatException ex) {
        StringBuilder ruta = new StringBuilder();
        for (var referencia : ex.getPath()) {
            if (referencia.getFieldName() != null) {
                if (ruta.length() > 0) {
                    ruta.append('.');
                }
                ruta.append(referencia.getFieldName());
            } else {
                ruta.append('[').append(referencia.getIndex()).append(']');
            }
        }
        return ruta.toString();
    }
}
