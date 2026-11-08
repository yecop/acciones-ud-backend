package co.edu.udistrital.accionesud.infrastructure.adapter.in.web;

import co.edu.udistrital.accionesud.application.port.in.ConsultarPortafolioUseCase;
import co.edu.udistrital.accionesud.infrastructure.adapter.in.web.dto.PortafolioResumenResponse;
import co.edu.udistrital.accionesud.infrastructure.adapter.in.web.dto.PosicionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adaptador de entrada (web) para el portafolio del usuario.
 * Expone los endpoints de resumen y de posiciones exigidos por SCRUM-25.
 */
@RestController
@RequestMapping("/api/v1/portafolio")
public class PortafolioController {

    private final ConsultarPortafolioUseCase consultarPortafolioUseCase;

    public PortafolioController(ConsultarPortafolioUseCase consultarPortafolioUseCase) {
        this.consultarPortafolioUseCase = consultarPortafolioUseCase;
    }

    /**
     * GET /api/v1/portafolio
     * Devuelve el resumen del valor total del portafolio. Para un usuario nuevo
     * refleja un portafolio vacio (valores en cero).
     */
    @GetMapping
    public PortafolioResumenResponse obtenerResumen(
            @RequestHeader(name = "X-Usuario-Id", defaultValue = "usuario-demo") String usuarioId) {
        return PortafolioResumenResponse.desde(
                consultarPortafolioUseCase.consultarResumen(usuarioId));
    }

    /**
     * GET /api/v1/portafolio/posiciones
     * Devuelve el detalle de posiciones: activo, cantidad, precio de compra,
     * valor actual (desde la cache), ganancia/perdida y su porcentaje.
     * Para un usuario nuevo retorna una lista vacia.
     */
    @GetMapping("/posiciones")
    public List<PosicionResponse> obtenerPosiciones(
            @RequestHeader(name = "X-Usuario-Id", defaultValue = "usuario-demo") String usuarioId) {
        return consultarPortafolioUseCase.consultarPosiciones(usuarioId).stream()
                .map(PosicionResponse::desde)
                .collect(Collectors.toList());
    }
}
