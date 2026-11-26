package co.edu.udistrital.accionesud.cuenta.application.dto;

import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import java.util.Objects;

/**
 * Resultado de iniciar una recarga con pasarela.
 *
 * @param movimiento el depósito, {@code PENDIENTE} hasta que la pasarela confirme el pago.
 * @param urlPago    página de pago; {@code null} si la sesión ya no está abierta (pagada o vencida).
 * @param repetido   {@code true} si se repitió una {@code Idempotency-Key} ya usada.
 */
public record ResultadoRecarga(MovimientoFondos movimiento, String urlPago, boolean repetido) {

    public ResultadoRecarga {
        Objects.requireNonNull(movimiento);
    }
}
