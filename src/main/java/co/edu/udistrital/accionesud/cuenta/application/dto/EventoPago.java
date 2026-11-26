package co.edu.udistrital.accionesud.cuenta.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Aviso de la pasarela sobre una recarga, ya verificado (firma) y traducido por el adaptador.
 *
 * @param proveedor     p. ej. {@code STRIPE}; junto con {@code eventoId} identifica el aviso.
 * @param eventoId      id del evento en el proveedor (se procesa una sola vez).
 * @param resultado     qué significa para la recarga.
 * @param referencia    id de la sesión de pago en el proveedor.
 * @param movimientoId  movimiento {@code PENDIENTE} que originó la sesión; {@code null} si no vino.
 * @param montoCentavos monto cobrado en la unidad mínima de la moneda.
 * @param moneda        código ISO en minúsculas o mayúsculas (p. ej. {@code usd}).
 */
public record EventoPago(String proveedor, String eventoId, Resultado resultado, String referencia,
                         UUID movimientoId, long montoCentavos, String moneda) {

    public enum Resultado {
        /** El pago se cobró: hay que acreditar. */
        PAGADO,
        /** El pago no se completará (sesión vencida o pago rechazado). */
        NO_PAGADO,
        /** Evento que no cambia la recarga (p. ej. un pago asíncrono todavía en curso). */
        IGNORADO
    }

    public EventoPago {
        Objects.requireNonNull(proveedor);
        Objects.requireNonNull(eventoId);
        Objects.requireNonNull(resultado);
    }
}
