package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoConfiguradaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoDisponibleException;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error.ErrorResponse;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de la pasarela de pagos en el formato común: 502 {@code PASARELA_NO_DISPONIBLE} y 503
 * {@code PASARELA_NO_CONFIGURADA}. Tiene prioridad sobre el {@code GlobalExceptionHandler}, que
 * enviaría las excepciones de dominio a 422.
 */
@RestControllerAdvice(assignableTypes = CuentaController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CuentaExceptionHandler {

    private final ErrorResponseFactory factory;

    public CuentaExceptionHandler(ErrorResponseFactory factory) {
        this.factory = factory;
    }

    @ExceptionHandler(PasarelaNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> pasarelaNoDisponible(PasarelaNoDisponibleException ex,
                                                              HttpServletRequest request) {
        return responder(HttpStatus.BAD_GATEWAY, ex.getCodigo(), ex.getMessage(), request);
    }

    @ExceptionHandler(PasarelaNoConfiguradaException.class)
    public ResponseEntity<ErrorResponse> pasarelaNoConfigurada(PasarelaNoConfiguradaException ex,
                                                               HttpServletRequest request) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, ex.getCodigo(), ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String codigo, String mensaje,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(status).body(factory.crear(request, status, codigo, mensaje, List.of(), Map.of()));
    }
}
