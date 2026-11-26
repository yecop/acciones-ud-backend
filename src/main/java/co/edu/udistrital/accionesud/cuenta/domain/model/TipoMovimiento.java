package co.edu.udistrital.accionesud.cuenta.domain.model;

/**
 * Tipos de movimiento de fondos de la tabla {@code movimiento_fondos} (V14, SCRUM-32).
 *
 * <p>SCRUM-46 expone {@link #DEPOSITO} y {@link #RETIRO}. {@link #RESERVA},
 * {@link #LIBERACION} y {@link #LIQUIDACION} quedan para el flujo de órdenes (SCRUM-38 y 45).</p>
 */
public enum TipoMovimiento {
    DEPOSITO,
    RETIRO,
    RESERVA,
    LIBERACION,
    LIQUIDACION;

    /** {@code true} si el movimiento aumenta el saldo disponible. */
    public boolean acredita() {
        return this == DEPOSITO || this == LIBERACION;
    }
}
