package co.edu.udistrital.accionesud.infrastructure.adapter.in.web.dto;

import co.edu.udistrital.accionesud.application.dto.PosicionValorada;
import co.edu.udistrital.accionesud.infrastructure.adapter.in.web.util.MoneyFormatter;

/**
 * Respuesta JSON de cada posicion en GET /api/v1/portafolio/posiciones.
 *
 * <p>Expone activo, cantidad, precio de compra, precio actual (de la cache),
 * valor actual, ganancia/perdida y su porcentaje. Todos los montos como texto
 * con 2 decimales.
 */
public class PosicionResponse {

    private final String activo;
    private final String cantidad;
    private final String precioCompra;
    private final String precioActual;
    private final String valorActual;
    private final String gananciaPerdida;
    private final String porcentajeGananciaPerdida;

    public PosicionResponse(String activo,
                            String cantidad,
                            String precioCompra,
                            String precioActual,
                            String valorActual,
                            String gananciaPerdida,
                            String porcentajeGananciaPerdida) {
        this.activo = activo;
        this.cantidad = cantidad;
        this.precioCompra = precioCompra;
        this.precioActual = precioActual;
        this.valorActual = valorActual;
        this.gananciaPerdida = gananciaPerdida;
        this.porcentajeGananciaPerdida = porcentajeGananciaPerdida;
    }

    public static PosicionResponse desde(PosicionValorada posicion) {
        return new PosicionResponse(
                posicion.getActivo(),
                MoneyFormatter.format(posicion.getCantidad()),
                MoneyFormatter.format(posicion.getPrecioCompra()),
                MoneyFormatter.format(posicion.getPrecioActual()),
                MoneyFormatter.format(posicion.getValorActual()),
                MoneyFormatter.format(posicion.getGananciaPerdida()),
                MoneyFormatter.format(posicion.getPorcentajeGananciaPerdida()));
    }

    public String getActivo() {
        return activo;
    }

    public String getCantidad() {
        return cantidad;
    }

    public String getPrecioCompra() {
        return precioCompra;
    }

    public String getPrecioActual() {
        return precioActual;
    }

    public String getValorActual() {
        return valorActual;
    }

    public String getGananciaPerdida() {
        return gananciaPerdida;
    }

    public String getPorcentajeGananciaPerdida() {
        return porcentajeGananciaPerdida;
    }
}
