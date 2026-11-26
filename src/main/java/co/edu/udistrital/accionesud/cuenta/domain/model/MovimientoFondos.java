package co.edu.udistrital.accionesud.cuenta.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Movimiento de fondos de una cuenta (SCRUM-46). Queda registrado aunque se rechace, con su
 * estado y el traceId de la solicitud que lo originó.
 *
 * @param completadoEn instante en que se aplicó al saldo; {@code null} si no se aplicó.
 */
public record MovimientoFondos(UUID id,
                               UUID cuentaId,
                               TipoMovimiento tipo,
                               EstadoMovimiento estado,
                               BigDecimal monto,
                               String moneda,
                               Instant creadoEn,
                               Instant completadoEn,
                               String traceId) {

    public MovimientoFondos {
        Objects.requireNonNull(id, "El id del movimiento es obligatorio");
        Objects.requireNonNull(cuentaId, "La cuenta del movimiento es obligatoria");
        Objects.requireNonNull(tipo, "El tipo del movimiento es obligatorio");
        Objects.requireNonNull(estado, "El estado del movimiento es obligatorio");
        Objects.requireNonNull(monto, "El monto del movimiento es obligatorio");
        Objects.requireNonNull(moneda, "La moneda del movimiento es obligatoria");
        Objects.requireNonNull(creadoEn, "La fecha del movimiento es obligatoria");
        if (monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto del movimiento debe ser positivo");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("El traceId del movimiento es obligatorio");
        }
        if (estado == EstadoMovimiento.COMPLETADO && completadoEn == null) {
            throw new IllegalArgumentException("Un movimiento completado necesita su fecha de aplicación");
        }
    }

    /** ¿Es la misma operación (tipo y monto) que se pide repetir con la misma clave? */
    public boolean mismaOperacion(TipoMovimiento otroTipo, BigDecimal otroMonto) {
        return tipo == otroTipo && monto.compareTo(otroMonto) == 0;
    }
}
