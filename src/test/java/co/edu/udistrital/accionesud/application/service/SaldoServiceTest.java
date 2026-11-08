package co.edu.udistrital.accionesud.application.service;

import co.edu.udistrital.accionesud.application.port.out.SaldoRepositoryPort;
import co.edu.udistrital.accionesud.domain.model.Saldo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Pruebas del caso de uso de saldo, con enfasis en la regla del saldo inicial
 * simulado para usuarios nuevos (SCRUM-25).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SaldoService")
class SaldoServiceTest {

    private static final BigDecimal SALDO_INICIAL = new BigDecimal("100000.00");

    @Mock
    private SaldoRepositoryPort saldoRepository;

    private SaldoService service;

    @BeforeEach
    void setUp() {
        service = new SaldoService(saldoRepository, SALDO_INICIAL);
    }

    @Test
    @DisplayName("usuario nuevo recibe el saldo inicial simulado con reservado 0")
    void usuarioNuevoRecibeSaldoInicial() {
        when(saldoRepository.buscarPorUsuario("nuevo")).thenReturn(Optional.empty());

        Saldo saldo = service.consultarSaldo("nuevo");

        assertEquals(SALDO_INICIAL, saldo.getDisponible());
        assertEquals(BigDecimal.ZERO, saldo.getReservado());
        assertEquals(SALDO_INICIAL, saldo.getTotal());
    }

    @Test
    @DisplayName("usuario existente conserva su saldo persistido")
    void usuarioExistenteConservaSaldo() {
        Saldo persistido = new Saldo(new BigDecimal("500.00"), new BigDecimal("250.00"));
        when(saldoRepository.buscarPorUsuario("viejo")).thenReturn(Optional.of(persistido));

        Saldo saldo = service.consultarSaldo("viejo");

        assertEquals(new BigDecimal("500.00"), saldo.getDisponible());
        assertEquals(new BigDecimal("250.00"), saldo.getReservado());
        assertEquals(new BigDecimal("750.00"), saldo.getTotal());
    }

    @Test
    @DisplayName("el saldo inicial es parametrizable")
    void saldoInicialParametrizable() {
        SaldoService otro = new SaldoService(saldoRepository, new BigDecimal("5000.00"));
        when(saldoRepository.buscarPorUsuario("nuevo")).thenReturn(Optional.empty());

        Saldo saldo = otro.consultarSaldo("nuevo");

        assertEquals(new BigDecimal("5000.00"), saldo.getDisponible());
    }
}
