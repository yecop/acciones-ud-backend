package co.edu.udistrital.accionesud.cuenta.application.dto;

import java.math.BigDecimal;

/**
 * Resultado de aplicacion que representa una posicion ya valorada con el precio
 * actual de la cache. Contiene todos los valores que exige SCRUM-25 para el
 * endpoint de posiciones.
 *
 * <p>Los montos se mantienen como {@link BigDecimal} en esta capa; el formateo
 * a texto con 2 decimales se realiza en el adaptador web.
 */
public final class PosicionValorada {

    private final String activo;
    private final BigDecimal cantidad;
    private final BigDecimal precioCompra;
    private final BigDecimal precioActual;
    private final BigDecimal valorActual;
    private final BigDecimal gananciaPerdida;
    private final BigDecimal porcentajeGananciaPerdida;

    public PosicionValorada(String activo,
                            BigDecimal cantidad,
                            BigDecimal precioCompra,
                            BigDecimal precioActual,
                            BigDecimal valorActual,
                            BigDecimal gananciaPerdida,
                            BigDecimal porcentajeGananciaPerdida) {
        this.activo = activo;
        this.cantidad = cantidad;
        this.precioCompra = precioCompra;
        this.precioActual = precioActual;
        this.valorActual = valorActual;
        this.gananciaPerdida = gananciaPerdida;
        this.porcentajeGananciaPerdida = porcentajeGananciaPerdida;
    }

    public String getActivo() {
        return activo;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioCompra() {
        return precioCompra;
    }

    public BigDecimal getPrecioActual() {
        return precioActual;
    }

    public BigDecimal getValorActual() {
        return valorActual;
    }

    public BigDecimal getGananciaPerdida() {
        return gananciaPerdida;
    }

    public BigDecimal getPorcentajeGananciaPerdida() {
        return porcentajeGananciaPerdida;
    }
}
