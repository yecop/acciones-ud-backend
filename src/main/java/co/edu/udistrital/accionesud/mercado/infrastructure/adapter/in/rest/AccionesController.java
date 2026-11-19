package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.application.dto.FiltroAcciones;
import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarAccionesUseCase;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Sector;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.AccionResponse;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.CotizacionResponse;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adaptador de entrada REST de acciones y cotizaciones (SCRUM-24).
 *
 * <p>Todos los endpoints leen exclusivamente de la caché: ninguna petición de usuario
 * provoca una llamada a Alpaca.</p>
 */
@RestController
@Validated
@RequestMapping("/api/v1/acciones")
@Tag(name = "Acciones", description = "Catálogo de acciones y cotizaciones servidas desde caché")
public class AccionesController {

    private final ConsultarAccionesUseCase consultarAcciones;

    public AccionesController(ConsultarAccionesUseCase consultarAcciones) {
        this.consultarAcciones = consultarAcciones;
    }

    @GetMapping
    @Operation(summary = "Lista paginada de acciones con filtros por mercado, sector y texto libre")
    public ResponseEntity<PaginaResponse<AccionResponse>> listar(
            @Parameter(description = "Código de mercado: NYSE, NASDAQ, LSE, TSE, ASX")
            @RequestParam(required = false) String mercado,
            @Parameter(description = "Sector: TECNOLOGIA, FINANZAS, SALUD, ENERGIA, CONSUMO, MATERIALES")
            @RequestParam(required = false) String sector,
            @Parameter(description = "Texto a buscar en el ticker o el nombre")
            @RequestParam(required = false) @Size(max = 50) String q,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        FiltroAcciones filtro = new FiltroAcciones(parsearMercado(mercado), parsearSector(sector), q);
        return ResponseEntity.ok(AccionesRestMapper.aPaginaResponse(
                consultarAcciones.listar(filtro, pagina, tamano)));
    }

    @GetMapping("/{ticker}")
    @Operation(summary = "Detalle de una acción con su última cotización en caché")
    @ApiResponse(responseCode = "404", description = "ACCION_NO_ENCONTRADA")
    public ResponseEntity<AccionResponse> obtener(@PathVariable String ticker) {
        return ResponseEntity.ok(AccionesRestMapper.aAccionResponse(consultarAcciones.obtener(ticker)));
    }

    @GetMapping("/{ticker}/cotizacion")
    @Operation(summary = "Cotización de la acción (precio, bid, ask, spread, volumen, actualizadoEn, fuente)",
            description = "Siempre lee de la caché. Si el dato de EE. UU. tiene más de 15 s o el proveedor "
                    + "falla, se incluye una advertencia. Si nunca hubo dato, responde 502.")
    @ApiResponse(responseCode = "502", description = "PROVEEDOR_NO_DISPONIBLE: no hay dato en caché")
    public ResponseEntity<CotizacionResponse> cotizacion(@PathVariable String ticker) {
        return ResponseEntity.ok(AccionesRestMapper.aCotizacionResponse(
                consultarAcciones.obtenerCotizacion(ticker)));
    }

    private CodigoMercado parsearMercado(String mercado) {
        if (mercado == null || mercado.isBlank()) {
            return null;
        }
        try {
            return CodigoMercado.desde(mercado);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mercado no válido: " + mercado);
        }
    }

    private Sector parsearSector(String sector) {
        if (sector == null || sector.isBlank()) {
            return null;
        }
        try {
            return Sector.desde(sector);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sector no válido: " + sector);
        }
    }
}
