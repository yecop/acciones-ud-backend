package co.edu.udistrital.accionesud.ordenes.domain.service;

import co.edu.udistrital.accionesud.ordenes.domain.model.ComisionCalculada;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Cálculo puro de total, comisión y neto con aritmética decimal exacta. */
public final class CalculadoraComision {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int ESCALA_MONETARIA = 2;

    public ComisionCalculada calcular(BigDecimal precio, int cantidad, BigDecimal porcentaje,
                                      BigDecimal repartoPlataforma, boolean tieneComisionista, LadoOrden lado) {
        Objects.requireNonNull(precio, "El precio es obligatorio");
        Objects.requireNonNull(porcentaje, "El porcentaje es obligatorio");
        Objects.requireNonNull(repartoPlataforma, "El reparto de plataforma es obligatorio");
        Objects.requireNonNull(lado, "El lado de la orden es obligatorio");
        if (precio.signum() <= 0) {
            throw new IllegalArgumentException("El precio debe ser positivo");
        }
        if (cantidad < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1");
        }
        validarPorcentaje(porcentaje, "porcentaje");
        validarPorcentaje(repartoPlataforma, "repartoPlataforma");

        BigDecimal total = precio.multiply(BigDecimal.valueOf(cantidad))
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        BigDecimal valor = total.multiply(porcentaje).divide(CIEN, ESCALA_MONETARIA, RoundingMode.HALF_UP);
        BigDecimal plataforma = tieneComisionista
                ? valor.multiply(repartoPlataforma).divide(CIEN, ESCALA_MONETARIA, RoundingMode.HALF_UP)
                : valor;
        BigDecimal comisionista = valor.subtract(plataforma).setScale(ESCALA_MONETARIA, RoundingMode.UNNECESSARY);
        BigDecimal neto = (lado == LadoOrden.COMPRA ? total.add(valor) : total.subtract(valor))
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return new ComisionCalculada(total, porcentaje, valor, plataforma, comisionista, neto);
    }

    private static void validarPorcentaje(BigDecimal porcentaje, String nombre) {
        if (porcentaje.signum() < 0 || porcentaje.compareTo(CIEN) > 0) {
            throw new IllegalArgumentException("El " + nombre + " debe estar entre 0 y 100");
        }
    }
}
