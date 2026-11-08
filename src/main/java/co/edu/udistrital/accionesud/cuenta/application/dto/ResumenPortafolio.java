package co.edu.udistrital.accionesud.cuenta.application.dto;

import java.math.BigDecimal;

/**
 * Resultado de aplicacion con el resumen del portafolio: valor total de
 * mercado, costo total invertido, ganancia/perdida total y su porcentaje,
 * ademas del numero de posiciones.
 */
public final class ResumenPortafolio {

    private final BigDecimal valorTotal;
    private final BigDecimal costoTotal;
    private final BigDecimal gananciaPerdidaTotal;
    private final BigDecimal porcentajeGananciaPerdidaTotal;
    private final int numeroPosiciones;

    public ResumenPortafolio(BigDecimal valorTotal,
                             BigDecimal costoTotal,
                             BigDecimal gananciaPerdidaTotal,
                             BigDecimal porcentajeGananciaPerdidaTotal,
                             int numeroPosiciones) {
        this.valorTotal = valorTotal;
        this.costoTotal = costoTotal;
        this.gananciaPerdidaTotal = gananciaPerdidaTotal;
        this.porcentajeGananciaPerdidaTotal = porcentajeGananciaPerdidaTotal;
        this.numeroPosiciones = numeroPosiciones;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public BigDecimal getCostoTotal() {
        return costoTotal;
    }

    public BigDecimal getGananciaPerdidaTotal() {
        return gananciaPerdidaTotal;
    }

    public BigDecimal getPorcentajeGananciaPerdidaTotal() {
        return porcentajeGananciaPerdidaTotal;
    }

    public int getNumeroPosiciones() {
        return numeroPosiciones;
    }
}
