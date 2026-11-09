package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory factory;

    public GlobalExceptionHandler(ErrorResponseFactory factory) {
        this.factory = factory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        List<FieldError> errores = ex.getBindingResult().getFieldErrors();
        List<String> detalles = errores.stream()
                .map(error -> error.getField() + ": " + mensaje(error.getDefaultMessage()))
                .toList();
        List<Map<String, String>> campos = errores.stream()
                .map(error -> Map.of(
                        "campo", error.getField(),
                        "mensaje", mensaje(error.getDefaultMessage())))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, CodigoError.ERROR_VALIDACION,
                detalles, Map.of("campos", campos), request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> manejarValidacionDeMetodo(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<String> detalles = ex.getAllValidationResults().stream()
                .flatMap(resultado -> {
                    String parametro = resultado.getMethodParameter().getParameterName();
                    String nombre = parametro != null ? parametro : "parámetro";
                    return resultado.getResolvableErrors().stream()
                            .map(error -> nombre + ": " + mensaje(error.getDefaultMessage()));
                })
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, CodigoError.ERROR_VALIDACION,
                detalles, Map.of(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> manejarRestriccion(ConstraintViolationException ex,
                                                            HttpServletRequest request) {
        List<String> detalles = ex.getConstraintViolations().stream()
                .map(violacion -> violacion.getPropertyPath() + ": " + violacion.getMessage())
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, CodigoError.ERROR_VALIDACION,
                detalles, Map.of(), request);
    }

    @ExceptionHandler({
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> manejarParametrosInvalidos(Exception ex,
                                                                    HttpServletRequest request) {
        return respuesta(HttpStatus.BAD_REQUEST, CodigoError.SOLICITUD_INVALIDA,
                List.of(ex.getMessage()), Map.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarTipoContenido(HttpMediaTypeNotSupportedException ex,
                                                              HttpServletRequest request) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CodigoError.TIPO_CONTENIDO_NO_SOPORTADO,
                List.of(), Map.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, CodigoError.METODO_NO_PERMITIDO,
                List.of(), Map.of(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> manejarRecursoNoEncontrado(Exception ex,
                                                                    HttpServletRequest request) {
        return respuesta(HttpStatus.NOT_FOUND, CodigoError.RECURSO_NO_ENCONTRADO,
                List.of(), Map.of(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> manejarResponseStatus(ResponseStatusException ex,
                                                               HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        CodigoError codigo = CodigoError.desdeStatus(status);
        String mensaje = ex.getReason() != null ? ex.getReason() : codigo.getMensaje();
        return respuesta(status, codigo.name(), mensaje, List.of(), Map.of(), request);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> manejarExcepcionDominio(DomainException ex,
                                                                 HttpServletRequest request) {
        HttpStatus status = ex.getCodigo() != null && ex.getCodigo().contains("NO_ENCONTRADO")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return respuesta(status, ex.getCodigo(), ex.getMessage(), List.of(), Map.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> manejarNoAutenticado(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return respuesta(HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO,
                List.of(), Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        return respuesta(HttpStatus.FORBIDDEN, CodigoError.ACCESO_DENEGADO,
                List.of(), Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInterno(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO,
                List.of(), Map.of(), request);
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, CodigoError codigo,
                                                    List<String> detalles, Map<String, Object> datos,
                                                    HttpServletRequest request) {
        return respuesta(status, codigo.name(), codigo.getMensaje(), detalles, datos, request);
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String codigo, String mensaje,
                                                    List<String> detalles, Map<String, Object> datos,
                                                    HttpServletRequest request) {
        ErrorResponse cuerpo = factory.crear(request, status, codigo, mensaje, detalles, datos);
        return ResponseEntity.status(status).body(cuerpo);
    }

    private String mensaje(String valor) {
        return Objects.requireNonNullElse(valor, "valor inválido");
    }
}
