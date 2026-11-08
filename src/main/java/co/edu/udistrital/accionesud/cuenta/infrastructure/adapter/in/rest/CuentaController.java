package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto.SaldoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Adaptador de entrada (web) para la cuenta del usuario.
 * Expone el endpoint de saldo exigido por SCRUM-25.
 */
@RestController
@RequestMapping("/api/v1/cuenta")
public class CuentaController {

    private final ConsultarSaldoUseCase consultarSaldoUseCase;

    public CuentaController(ConsultarSaldoUseCase consultarSaldoUseCase) {
        this.consultarSaldoUseCase = consultarSaldoUseCase;
    }

    /**
     * GET /api/v1/cuenta/saldo
     * Devuelve el saldo disponible y reservado. Para un usuario nuevo retorna
     * el saldo inicial simulado.
     */
    @GetMapping("/saldo")
    public SaldoResponse obtenerSaldo(
            @RequestHeader(name = "X-Usuario-Id", defaultValue = "usuario-demo") String usuarioId) {
        Saldo saldo = consultarSaldoUseCase.consultarSaldo(usuarioId);
        return SaldoResponse.desde(saldo);
    }

    /**
     * GET /api/v1/cuenta/health
     * Comprueba que el modulo de cuenta (SCRUM-25) esta activo. Util para
     * verificaciones rapidas de despliegue e integracion.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "modulo", "cuenta",
                "historia", "SCRUM-25",
                "estado", "activo");
    }
}
