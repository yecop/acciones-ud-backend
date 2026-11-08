package co.edu.udistrital.accionesud.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Entidad de dominio que representa una posicion abierta sobre un activo.
 *
 * <p>Una posicion conoce:
 * <ul>
 *     <li>El activo (ticker, ej. "AAPL").</li>
 *     <li>La cantidad de unidades/acciones.</li>
 *     <li>El precio promedio de compra.</li>
 * </ul>
 *
 * <p>Toda la logica de valoracion y de calculo de ganancia/perdida vive aqui,
 * en el dominio. Esta clase es la que validan de forma exhaustiva las pruebas
 * unitarias (criterio de aceptacion SCRUM-25).
 *
 * <p>Convenciones de calculo:
 * <ul>
 *     <li>Costo base = cantidad * precioCompra.</li>
 *     <li>Valor actual = cantidad * precioActual.</li>
 *     <li>Ganancia/Perdida (P&amp;L) = valorActual - costoBase.</li>
 *     <li>Porcentaje = (P&amp;L / costoBase) * 100. Si el costo base es cero,
 *         el porcentaje se define como cero para evitar division por cero.</li>
 * </ul>
 * Los montos monetarios se redondean a 2 decimales (HALF_UP) y el porcentaje
 * a 2 decimales (HALF_UP).
 */
public final class Posicion {

    /** Escala (decimales) para valores monetarios. */
    public static final int ESCALA_MONETARIA = 2;
    /** Escala (decimales) para el porcentaje de ganancia/perdida. */
    public static final int ESCALA_PORCENTAJE = 2;

    private final String activo;
    private final BigDecimal cantidad;
    private final BigDecimal precioCompra;

    public Posicion(String activo, BigDecimal cantidad, BigDecimal precioCompra) {
        this.activo = requireActivo(activo);
        this.cantidad = requireNonNegative(cantidad, "cantidad");
        this.precioCompra = requireNonNegative(precioCompra, "precioCompra");
    }

    private static String requireActivo(String activo) {
        Objects.requireNonNull(activo, "El activo no puede ser nulo");
        if (activo.trim().isEmpty()) {
            throw new IllegalArgumentException("El activo no puede estar vacio");
        }
        return activo.trim().toUpperCase();
    }

    private static BigDecimal requireNonNegative(BigDecimal valor, String nombre) {
        Objects.requireNonNull(valor, "El campo '" + nombre + "' no puede ser nulo");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("El campo '" + nombre + "' no puede ser negativo");
        }
        return valor;
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

    /** Costo base de la posicion = cantidad * precioCompra (2 decimales). */
    public BigDecimal costoBase() {
        return cantidad.multiply(precioCompra).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Valor actual de la posicion = cantidad * precioActual (2 decimales).
     *
     * @param precioActual precio de mercado vigente tomado de la cache.
     */
    public BigDecimal valorActual(BigDecimal precioActual) {
        Objects.requireNonNull(precioActual, "El precio actual no puede ser nulo");
        return cantidad.multiply(precioActual).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Ganancia o perdida absoluta = valorActual - costoBase (2 decimales).
     * Un valor positivo es ganancia; uno negativo, perdida.
     *
     * @param precioActual precio de mercado vigente tomado de la cache.
     */
    public BigDecimal gananciaPerdida(BigDecimal precioActual) {
        return valorActual(precioActual).subtract(costoBase())
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Porcentaje de ganancia/perdida respecto al costo base.
     * Formula: (gananciaPerdida / costoBase) * 100, redondeado a 2 decimales.
     * Si el costo base es cero, retorna 0 para evitar division por cero.
     *
     * @param precioActual precio de mercado vigente tomado de la cache.
     */
    public BigDecimal porcentajeGananciaPerdida(BigDecimal precioActual) {
        BigDecimal base = costoBase();
        if (base.signum() == 0) {
            return BigDecimal.ZERO.setScale(ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
        }
        return gananciaPerdida(precioActual)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Posicion)) {
            return false;
        }
        Posicion posicion = (Posicion) o;
        return activo.equals(posicion.activo)
                && cantidad.compareTo(posicion.cantidad) == 0
                && precioCompra.compareTo(posicion.precioCompra) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(activo, cantidad.stripTrailingZeros(), precioCompra.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "Posicion{activo='" + activo + "', cantidad=" + cantidad
                + ", precioCompra=" + precioCompra + '}';
    }
}
