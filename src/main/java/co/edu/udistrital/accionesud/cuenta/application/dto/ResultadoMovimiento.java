package co.edu.udistrital.accionesud.cuenta.application.dto;

import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import java.util.Objects;

/**
 * Resultado de un depósito o retiro.
 *
 * @param repetido {@code true} si la solicitud repitió una {@code Idempotency-Key} ya usada y se
 *                 devolvió el movimiento original sin volver a aplicarlo.
 */
public record ResultadoMovimiento(MovimientoFondos movimiento, Saldo saldo, boolean repetido) {

    public ResultadoMovimiento {
        Objects.requireNonNull(movimiento);
        Objects.requireNonNull(saldo);
    }
}
