package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.ordenes.application.port.in.SimularOrdenUseCase;
import co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.in.rest.dto.SimularOrdenRequest;
import co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.in.rest.dto.SimulacionOrdenResponse;
import co.edu.udistrital.accionesud.shared.application.port.out.AccesoComisionistaPort;
import co.edu.udistrital.accionesud.shared.domain.exception.AccesoDenegadoException;
import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** Endpoint de consulta previo a confirmar una orden. No guarda órdenes ni altera saldos. */
@RestController
@RequestMapping("/api/v1/ordenes")
@Tag(name = "Órdenes", description = "Simulación de costos antes de confirmar operaciones")
public class OrdenesController {

    private final SimularOrdenUseCase simulador;
    private final AccesoComisionistaPort accesoComisionista;

    public OrdenesController(SimularOrdenUseCase simulador, AccesoComisionistaPort accesoComisionista) {
        this.simulador = simulador;
        this.accesoComisionista = accesoComisionista;
    }

    @PostMapping("/simulacion")
    @PreAuthorize("hasAnyRole('INVERSIONISTA', 'COMISIONISTA')")
    @Operation(summary = "Simula el costo de una orden sin crearla ni reservar saldo")
    public SimulacionOrdenResponse simular(Authentication autenticacion,
                                            @Valid @RequestBody SimularOrdenRequest solicitud) {
        UUID actorId = idAutenticado(autenticacion);
        boolean esComisionista = autenticacion.getAuthorities().stream()
                .anyMatch(a -> "ROLE_COMISIONISTA".equals(a.getAuthority()));
        UUID inversionistaId;
        if (esComisionista) {
            inversionistaId = solicitud.inversionistaId();
            if (inversionistaId == null) {
                throw new ValidacionException("inversionistaId", "es obligatorio para un comisionista");
            }
            accesoComisionista.exigirAcceso(actorId, inversionistaId);
        } else {
            inversionistaId = actorId;
            if (solicitud.inversionistaId() != null && !actorId.equals(solicitud.inversionistaId())) {
                throw new AccesoDenegadoException("El inversionista solo puede simular para su propia cuenta");
            }
            esComisionista = accesoComisionista.comisionistaVigenteDe(inversionistaId).isPresent();
        }
        return SimulacionOrdenResponse.desde(simulador.simular(inversionistaId, solicitud.ticker(),
                solicitud.lado(), solicitud.tipo(), solicitud.cantidad(), solicitud.precioLimite(),
                solicitud.precioStop(), esComisionista));
    }

    private static UUID idAutenticado(Authentication autenticacion) {
        try {
            return UUID.fromString(autenticacion.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token no identifica a un usuario");
        }
    }
}
