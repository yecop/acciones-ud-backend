package co.edu.udistrital.accionesud.cuenta.domain.model;

/** Estados de un movimiento de fondos (CHECK de {@code movimiento_fondos.estado}, V14). */
public enum EstadoMovimiento {
    PENDIENTE,
    /** Se aplicó al saldo. */
    COMPLETADO,
    /** Se rechazó sin modificar el saldo (p. ej. retiro mayor al disponible). */
    FALLIDO,
    CANCELADO
}
