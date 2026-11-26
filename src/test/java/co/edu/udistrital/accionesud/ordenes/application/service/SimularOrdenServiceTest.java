package co.edu.udistrital.accionesud.ordenes.application.service;

import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.ordenes.application.port.out.ParametrosPort;
import co.edu.udistrital.accionesud.ordenes.application.port.out.PrecioSimulacionPort;
import co.edu.udistrital.accionesud.ordenes.application.dto.PrecioSimulacion;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;
import co.edu.udistrital.accionesud.ordenes.domain.service.CalculadoraComision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimularOrdenServiceTest {

    private static final UUID USUARIO = UUID.fromString("c50ed483-e20f-4ad7-96c3-4c381ee22fd4");
    private static final PrecioSimulacion PRECIO = new PrecioSimulacion("AAPL", "NASDAQ",
            new BigDecimal("175.00"), Instant.parse("2026-11-25T14:00:00Z"), false,
            null, null, true, null);

    @Test
    void compraSinFondosSuficientesSoloDevuelveLaValidacionYNoCambiaElSaldo() {
        Saldo saldoInicial = new Saldo(new BigDecimal("1000.00"), new BigDecimal("50.00"));
        SimularOrdenService servicio = servicio(saldoInicial, Map.of());

        var resultado = servicio.simular(USUARIO, "aapl", LadoOrden.COMPRA, 10, false);

        assertThat(resultado.fondosSuficientes()).isFalse();
        assertThat(resultado.comision().neto()).isEqualByComparingTo("1785.00");
        assertThat(saldoInicial.getDisponible()).isEqualByComparingTo("1000.00");
        assertThat(saldoInicial.getReservado()).isEqualByComparingTo("50.00");
    }

    @Test
    void ventaNoExigeSaldoDeEfectivoYUsaElParametroEspecificoDelMercadoSiExiste() {
        Saldo saldo = new Saldo(BigDecimal.ZERO, BigDecimal.ZERO);
        SimularOrdenService servicio = servicio(saldo,
                Map.of("comision.porcentaje.NASDAQ", new BigDecimal("1.00")));

        var resultado = servicio.simular(USUARIO, "AAPL", LadoOrden.VENTA, 10, false);

        assertThat(resultado.fondosSuficientes()).isTrue();
        assertThat(resultado.comision().porcentaje()).isEqualByComparingTo("1.00");
        assertThat(resultado.comision().neto()).isEqualByComparingTo("1732.50");
    }

    private SimularOrdenService servicio(Saldo saldo, Map<String, BigDecimal> valores) {
        PrecioSimulacionPort precios = ticker -> PRECIO;
        ParametrosPort parametros = clave -> Optional.ofNullable(valores.getOrDefault(clave,
                Map.of("comision.porcentaje", new BigDecimal("2.00"),
                        "comision.reparto.plataforma", new BigDecimal("60.00")).get(clave)));
        ConsultarSaldoUseCase saldos = usuario -> saldo;
        return new SimularOrdenService(precios, parametros, saldos, new CalculadoraComision());
    }
}
