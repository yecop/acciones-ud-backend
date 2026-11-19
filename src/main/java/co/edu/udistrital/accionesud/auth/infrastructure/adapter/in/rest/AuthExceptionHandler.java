package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.auth.domain.exception.AutenticacionException;
import co.edu.udistrital.accionesud.auth.domain.exception.DemasiadasSolicitudesException;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error.CodigoError;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error.ErrorResponse;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private final ErrorResponseFactory factory;

    public AuthExceptionHandler(ErrorResponseFactory factory) {
        this.factory = factory;
    }

    @ExceptionHandler(AutenticacionException.class)
    public ResponseEntity<ErrorResponse> manejarAutenticacion(AutenticacionException ex,
                                                               HttpServletRequest request) {
        CodigoError codigo = CodigoError.valueOf(ex.getCodigo().name());
        HttpStatus status = codigo.getStatus();
        Map<String, Object> datos = Map.of();

        if (ex instanceof DemasiadasSolicitudesException demasiadas) {
            datos = Map.of("reintentarEnSegundos", demasiadas.getReintentarEnSegundos());
        }

        ErrorResponse cuerpo = factory.crear(request, status, codigo.name(), codigo.getMensaje(),
                List.of(), datos);
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(status);
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            long segundos = ex instanceof DemasiadasSolicitudesException demasiadas
                    ? demasiadas.getReintentarEnSegundos() : 1;
            respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
        }
        return respuesta.body(cuerpo);
    }
}
