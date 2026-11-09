package co.edu.udistrital.accionesud.cuenta.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Entidad de dominio que representa el saldo de efectivo de una cuenta.
 *
 * <p>Se compone de:
 * <ul>
 *     <li><b>disponible</b>: dinero que el usuario puede usar para operar.</li>
 *     <li><b>reservado</b>: dinero comprometido en ordenes abiertas.</li>
 * </ul>
 *
 * El dominio no depende de ningun framework; toda la aritmetica monetaria usa
 * {@link BigDecimal} para evitar errores de redondeo propios de los tipos de
 * punto flotante.
 */
public final class Saldo {

    private final BigDecimal disponible;
    private final BigDecimal reservado;

    public Saldo(BigDecimal disponible, BigDecimal reservado) {
        this.disponible = requireNonNegative(disponible, "disponible");
        this.reservado = requireNonNegative(reservado, "reservado");
    }

    private static BigDecimal requireNonNegative(BigDecimal valor, String nombre) {
        Objects.requireNonNull(valor, "El saldo '" + nombre + "' no puede ser nulo");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("El saldo '" + nombre + "' no puede ser negativo");
        }
        return valor;
    }

    /** Dinero disponible para operar. */
    public BigDecimal getDisponible() {
        return disponible;
    }

    /** Dinero comprometido en ordenes abiertas. */
    public BigDecimal getReservado() {
        return reservado;
    }

    /** Suma de disponible + reservado. */
    public BigDecimal getTotal() {
        return disponible.add(reservado);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Saldo)) {
            return false;
        }
        Saldo saldo = (Saldo) o;
        return disponible.compareTo(saldo.disponible) == 0
                && reservado.compareTo(saldo.reservado) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(disponible.stripTrailingZeros(), reservado.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "Saldo{disponible=" + disponible + ", reservado=" + reservado + '}';
    }
}
