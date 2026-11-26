package co.edu.udistrital.accionesud.ordenes.application.service;

import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.ordenes.application.port.out.ParametrosPort;
import co.edu.udistrital.accionesud.ordenes.application.port.out.PrecioSimulacionPort;
import co.edu.udistrital.accionesud.ordenes.application.port.out.PosicionDisponiblePort;
import co.edu.udistrital.accionesud.ordenes.application.dto.PrecioSimulacion;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;
import co.edu.udistrital.accionesud.ordenes.domain.model.TipoOrden;
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

        var resultado = servicio.simular(USUARIO, "aapl", LadoOrden.COMPRA, TipoOrden.MARKET,
                10, null, null, false);

        assertThat(resultado.fondosSuficientes()).isFalse();
        assertThat(resultado.comision().neto()).isEqualByComparingTo("1785.00");
        assertThat(saldoInicial.getDisponible()).isEqualByComparingTo("1000.00");
        assertThat(saldoInicial.getReservado()).isEqualByComparingTo("50.00");
    }

    @Test
    void ventaValidaAccionesDisponiblesYUsaElParametroEspecificoDelMercadoSiExiste() {
        Saldo saldo = new Saldo(BigDecimal.ZERO, BigDecimal.ZERO);
        SimularOrdenService servicio = servicio(saldo,
                Map.of("comision.porcentaje.NASDAQ", new BigDecimal("1.00")), 10);

        var resultado = servicio.simular(USUARIO, "AAPL", LadoOrden.VENTA, TipoOrden.MARKET,
                10, null, null, false);

        assertThat(resultado.fondosSuficientes()).isTrue();
        assertThat(resultado.comision().porcentaje()).isEqualByComparingTo("1.00");
        assertThat(resultado.comision().neto()).isEqualByComparingTo("1732.50");
    }

    @Test
    void ventaConAccionesInsuficientesDevuelveFondosSuficientesFalse() {
        SimularOrdenService servicio = servicio(new Saldo(BigDecimal.ZERO, BigDecimal.ZERO), Map.of(), 4);

        var resultado = servicio.simular(USUARIO, "AAPL", LadoOrden.VENTA, TipoOrden.MARKET,
                5, null, null, false);

        assertThat(resultado.fondosSuficientes()).isFalse();
    }

    @Test
    void limitUsaPrecioLimiteEnElCalculo() {
        ParametrosPort parametros = clave -> Optional.ofNullable(Map.of(
                "comision.porcentaje", new BigDecimal("2.00"),
                "comision.reparto.plataforma", new BigDecimal("60.00")).get(clave));
        PrecioSimulacion sinCotizacion = new PrecioSimulacion("AAPL", "NASDAQ", null, null,
                false, null, null, true, null);
        PrecioSimulacionPort precios = (ticker, obligatoria) -> {
            assertThat(obligatoria).isFalse();
            return sinCotizacion;
        };
        SimularOrdenService servicio = new SimularOrdenService(precios, parametros,
                usuario -> new Saldo(new BigDecimal("1000"), BigDecimal.ZERO),
                new CalculadoraComision(), (usuario, ticker) -> 0);

        var resultado = servicio.simular(USUARIO, "AAPL", LadoOrden.COMPRA, TipoOrden.LIMIT,
                2, new BigDecimal("100.00"), null, false);

        assertThat(resultado.precioEstimado()).isEqualByComparingTo("100.00");
        assertThat(resultado.comision().totalTransaccion()).isEqualByComparingTo("200.00");
        assertThat(resultado.comision().neto()).isEqualByComparingTo("204.00");
    }

    @Test
    void stopLossYTakeProfitUsanPrecioStopEnElCalculo() {
        SimularOrdenService servicio = servicio(new Saldo(new BigDecimal("1000"), BigDecimal.ZERO), Map.of());
        BigDecimal precioStop = new BigDecimal("120.00");

        var stopLoss = servicio.simular(USUARIO, "AAPL", LadoOrden.COMPRA, TipoOrden.STOP_LOSS,
                2, null, precioStop, false);
        var takeProfit = servicio.simular(USUARIO, "AAPL", LadoOrden.COMPRA, TipoOrden.TAKE_PROFIT,
                2, null, precioStop, false);

        assertThat(stopLoss.precioEstimado()).isEqualByComparingTo(precioStop);
        assertThat(takeProfit.precioEstimado()).isEqualByComparingTo(precioStop);
        assertThat(stopLoss.comision().totalTransaccion()).isEqualByComparingTo("240.00");
        assertThat(takeProfit.comision().totalTransaccion()).isEqualByComparingTo("240.00");
    }

    private SimularOrdenService servicio(Saldo saldo, Map<String, BigDecimal> valores) {
        return servicio(saldo, valores, 0);
    }

    private SimularOrdenService servicio(Saldo saldo, Map<String, BigDecimal> valores, long cantidadDisponible) {
        PrecioSimulacionPort precios = (ticker, obligatorio) -> PRECIO;
        ParametrosPort parametros = clave -> Optional.ofNullable(valores.getOrDefault(clave,
                Map.of("comision.porcentaje", new BigDecimal("2.00"),
                        "comision.reparto.plataforma", new BigDecimal("60.00")).get(clave)));
        ConsultarSaldoUseCase saldos = usuario -> saldo;
        PosicionDisponiblePort posiciones = (usuario, ticker) -> cantidadDisponible;
        return new SimularOrdenService(precios, parametros, saldos, new CalculadoraComision(), posiciones);
    }
}
