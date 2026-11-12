package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarEstadoMercadoUseCase;
import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarMercadosUseCase;
import co.edu.udistrital.accionesud.mercado.domain.exception.MercadoNoEncontradoException;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.EstadoMercadoSnapshot;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.EstadoMercadoResponse;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.MercadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada REST para la consulta de mercados y su estado operativo.
 */
@RestController
@RequestMapping("/api/v1/mercados")
@Tag(name = "Mercados", description = "Consulta de mercados y su estado de apertura/cierre")
public class MercadoController {

    private final ConsultarMercadosUseCase consultarMercados;
    private final ConsultarEstadoMercadoUseCase consultarEstado;

    public MercadoController(ConsultarMercadosUseCase consultarMercados,
                             ConsultarEstadoMercadoUseCase consultarEstado) {
        this.consultarMercados = consultarMercados;
        this.consultarEstado = consultarEstado;
    }

    @GetMapping
    @Operation(summary = "Lista los mercados soportados con sus horarios y zonas horarias")
    public ResponseEntity<List<MercadoResponse>> listar() {
        List<MercadoResponse> respuesta = consultarMercados.listarMercados().stream()
                .map(MercadoRestMapper::aMercadoResponse)
                .toList();
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{codigo}/estado")
    @Operation(summary = "Devuelve el estado actual del mercado: abierto/cerrado, "
            + "próxima apertura, próximo cierre y restricciones vigentes")
    public ResponseEntity<EstadoMercadoResponse> estado(@PathVariable String codigo) {
        CodigoMercado codigoMercado = parsearCodigo(codigo);
        EstadoMercadoSnapshot snapshot = consultarEstado.consultarEstado(codigoMercado);
        return ResponseEntity.ok(MercadoRestMapper.aEstadoResponse(snapshot));
    }

    private CodigoMercado parsearCodigo(String codigo) {
        try {
            return CodigoMercado.desde(codigo);
        } catch (IllegalArgumentException ex) {
            throw new MercadoNoEncontradoException(codigo);
        }
    }
}
