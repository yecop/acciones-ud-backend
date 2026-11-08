package co.edu.udistrital.accionesud.application.service;

import co.edu.udistrital.accionesud.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.application.port.out.SaldoRepositoryPort;
import co.edu.udistrital.accionesud.domain.model.Saldo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Servicio de aplicacion que implementa el caso de uso de consulta de saldo.
 *
 * <p>Regla de negocio SCRUM-25: si el usuario no tiene saldo registrado (usuario
 * nuevo), se le asigna un saldo inicial simulado y parametrizable como
 * disponible, con cero reservado.
 *
 * <p>Esta clase es agnostica del framework: el saldo inicial se recibe por
 * constructor y el wiring con Spring se hace en la capa de infraestructura.
 */
public class SaldoService implements ConsultarSaldoUseCase {

    private final SaldoRepositoryPort saldoRepository;
    private final BigDecimal saldoInicialSimulado;

    public SaldoService(SaldoRepositoryPort saldoRepository, BigDecimal saldoInicialSimulado) {
        this.saldoRepository = Objects.requireNonNull(saldoRepository, "saldoRepository");
        this.saldoInicialSimulado =
                Objects.requireNonNull(saldoInicialSimulado, "saldoInicialSimulado");
    }

    @Override
    public Saldo consultarSaldo(String usuarioId) {
        Objects.requireNonNull(usuarioId, "El usuarioId no puede ser nulo");
        return saldoRepository.buscarPorUsuario(usuarioId)
                .orElseGet(() -> new Saldo(saldoInicialSimulado, BigDecimal.ZERO));
    }
}
