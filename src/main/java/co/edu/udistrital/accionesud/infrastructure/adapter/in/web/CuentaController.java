package co.edu.udistrital.accionesud.infrastructure.adapter.in.web;

import co.edu.udistrital.accionesud.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.domain.model.Saldo;
import co.edu.udistrital.accionesud.infrastructure.adapter.in.web.dto.SaldoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
