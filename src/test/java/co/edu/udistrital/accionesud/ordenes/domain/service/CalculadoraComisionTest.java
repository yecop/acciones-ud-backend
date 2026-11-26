package co.edu.udistrital.accionesud.ordenes.domain.service;

import co.edu.udistrital.accionesud.ordenes.domain.model.ComisionCalculada;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraComisionTest {

    private final CalculadoraComision calculadora = new CalculadoraComision();

    @Test
    void calculaCompraYComisionCompletaParaLaPlataformaSinComisionista() {
        ComisionCalculada resultado = calculadora.calcular(new BigDecimal("175.00"), 10,
                new BigDecimal("2.00"), new BigDecimal("60.00"), false, LadoOrden.COMPRA);

        assertThat(resultado.totalTransaccion()).isEqualByComparingTo("1750.00");
        assertThat(resultado.valor()).isEqualByComparingTo("35.00");
        assertThat(resultado.valorPlataforma()).isEqualByComparingTo("35.00");
        assertThat(resultado.valorComisionista()).isEqualByComparingTo("0.00");
        assertThat(resultado.neto()).isEqualByComparingTo("1785.00");
    }

    @Test
    void ventaRestaLaComisionDelValorARecibir() {
        ComisionCalculada resultado = calculadora.calcular(new BigDecimal("175.00"), 10,
                new BigDecimal("2.00"), new BigDecimal("60.00"), false, LadoOrden.VENTA);

        assertThat(resultado.totalTransaccion()).isEqualByComparingTo("1750.00");
        assertThat(resultado.valor()).isEqualByComparingTo("35.00");
        assertThat(resultado.neto()).isEqualByComparingTo("1715.00");
    }

    @ParameterizedTest
    @CsvSource({"1750.00, 35.00, 21.00, 14.00", "875.50, 17.51, 10.51, 7.00"})
    void reparteLaComisionYElResiduoSiempreCuadra(String total, String valor,
                                                  String plataforma, String comisionista) {
        BigDecimal precio = new BigDecimal(total).divide(BigDecimal.TEN, 4, java.math.RoundingMode.HALF_UP);
        ComisionCalculada resultado = calculadora.calcular(precio, 10, new BigDecimal("2.00"),
                new BigDecimal("60.00"), true, LadoOrden.COMPRA);

        assertThat(resultado.totalTransaccion()).isEqualByComparingTo(total);
        assertThat(resultado.valor()).isEqualByComparingTo(valor);
        assertThat(resultado.valorPlataforma()).isEqualByComparingTo(plataforma);
        assertThat(resultado.valorComisionista()).isEqualByComparingTo(comisionista);
        assertThat(resultado.valorPlataforma().add(resultado.valorComisionista()))
                .isEqualByComparingTo(resultado.valor());
    }

    @Test
    void redondeaTotalYComisionConHalfUp() {
        ComisionCalculada resultado = calculadora.calcular(new BigDecimal("175.005"), 1,
                new BigDecimal("2.00"), new BigDecimal("60.00"), false, LadoOrden.COMPRA);

        assertThat(resultado.totalTransaccion()).isEqualByComparingTo("175.01");
        assertThat(resultado.valor()).isEqualByComparingTo("3.50");
        assertThat(resultado.neto()).isEqualByComparingTo("178.51");
    }

    @Test
    void rechazaPrecioCantidadYPorcentajesInvalidos() {
        assertThatThrownBy(() -> calculadora.calcular(BigDecimal.ZERO, 1, new BigDecimal("2"),
                new BigDecimal("60"), false, LadoOrden.COMPRA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculadora.calcular(BigDecimal.ONE, 0, new BigDecimal("2"),
                new BigDecimal("60"), false, LadoOrden.COMPRA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculadora.calcular(BigDecimal.ONE, 1, new BigDecimal("101"),
                new BigDecimal("60"), false, LadoOrden.COMPRA)).isInstanceOf(IllegalArgumentException.class);
    }
}
