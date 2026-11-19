package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.domain.exception.AccionNoEncontradaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduce las excepciones del servicio de cotizaciones al formato de error común.
 *
 * <ul>
 *   <li>{@link ProveedorNoDisponibleException} → 502 {@code PROVEEDOR_NO_DISPONIBLE}, con
 *   {@code Retry-After} igual al intervalo del actualizador del mercado de la acción
 *   (5 s en NYSE/NASDAQ; 60 s por defecto en LSE/TSE/ASX), que es lo mínimo que hay que
 *   esperar para que pueda haber un dato nuevo en caché. El mismo valor viaja en
 *   {@code datos.reintentarEnSegundos}.</li>
 *   <li>{@link AccionNoEncontradaException} → 404 {@code ACCION_NO_ENCONTRADA}.</li>
 * </ul>
 * Tiene prioridad sobre el {@code GlobalExceptionHandler}, que por defecto enviaría las
 * excepciones de dominio a 422.
 */
@RestControllerAdvice(assignableTypes = AccionesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccionesExceptionHandler {

    private final ErrorResponseFactory factory;
    private final MercadosProperties properties;

    public AccionesExceptionHandler(ErrorResponseFactory factory, MercadosProperties properties) {
        this.factory = factory;
        this.properties = properties;
    }

    @ExceptionHandler(ProveedorNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> proveedorNoDisponible(ProveedorNoDisponibleException ex,
                                                               HttpServletRequest request) {
        long reintentarEn = segundosHastaProximaActualizacion(ex.mercado().orElse(null), properties);
        Map<String, Object> datos = new LinkedHashMap<>();
        ex.mercado().ifPresent(m -> datos.put("mercado", m.name()));
        datos.put("reintentarEnSegundos", reintentarEn);

        ErrorResponse cuerpo = factory.crear(request, HttpStatus.BAD_GATEWAY, ex.getCodigo(),
                ex.getMessage(), List.of(), datos);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(reintentarEn))
                .body(cuerpo);
    }

    @ExceptionHandler(AccionNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> accionNoEncontrada(AccionNoEncontradaException ex,
                                                            HttpServletRequest request) {
        ErrorResponse cuerpo = factory.crear(request, HttpStatus.NOT_FOUND, ex.getCodigo(),
                ex.getMessage(), List.of(), Map.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cuerpo);
    }

    /**
     * Segundos (redondeados hacia arriba, mínimo 1) hasta el próximo ciclo del actualizador que
     * atiende al mercado. Sin mercado conocido se usa el intervalo mayor, para no invitar a
     * reintentar antes de tiempo.
     */
    static long segundosHastaProximaActualizacion(CodigoMercado mercado, MercadosProperties properties) {
        MercadosProperties.Cotizaciones cotizaciones = properties.getCotizaciones();
        long estadosUnidosMs = cotizaciones.getIntervaloMs();
        long internacionalMs = cotizaciones.getInternacional().getIntervaloMs();
        long intervaloMs;
        if (mercado == null) {
            intervaloMs = Math.max(estadosUnidosMs, internacionalMs);
        } else if (mercado == CodigoMercado.NYSE || mercado == CodigoMercado.NASDAQ) {
            intervaloMs = estadosUnidosMs;
        } else {
            intervaloMs = internacionalMs;
        }
        return Math.max(1, (intervaloMs + 999) / 1000);
    }
}
