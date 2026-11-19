package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.domain.exception.AccionNoEncontradaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
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

/**
 * Traduce las excepciones del servicio de cotizaciones al formato de error común.
 *
 * <ul>
 *   <li>{@link ProveedorNoDisponibleException} → 502 {@code PROVEEDOR_NO_DISPONIBLE}
 *   (con {@code Retry-After} igual al ciclo del actualizador).</li>
 *   <li>{@link AccionNoEncontradaException} → 404 {@code ACCION_NO_ENCONTRADA}.</li>
 * </ul>
 * Tiene prioridad sobre el {@code GlobalExceptionHandler}, que por defecto enviaría las
 * excepciones de dominio a 422.
 */
@RestControllerAdvice(assignableTypes = AccionesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccionesExceptionHandler {

    private static final String SEGUNDOS_REINTENTO = "5";

    private final ErrorResponseFactory factory;

    public AccionesExceptionHandler(ErrorResponseFactory factory) {
        this.factory = factory;
    }

    @ExceptionHandler(ProveedorNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> proveedorNoDisponible(ProveedorNoDisponibleException ex,
                                                               HttpServletRequest request) {
        ErrorResponse cuerpo = factory.crear(request, HttpStatus.BAD_GATEWAY, ex.getCodigo(),
                ex.getMessage(), List.of(), Map.of());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .header(HttpHeaders.RETRY_AFTER, SEGUNDOS_REINTENTO)
                .body(cuerpo);
    }

    @ExceptionHandler(AccionNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> accionNoEncontrada(AccionNoEncontradaException ex,
                                                            HttpServletRequest request) {
        ErrorResponse cuerpo = factory.crear(request, HttpStatus.NOT_FOUND, ex.getCodigo(),
                ex.getMessage(), List.of(), Map.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cuerpo);
    }
}
