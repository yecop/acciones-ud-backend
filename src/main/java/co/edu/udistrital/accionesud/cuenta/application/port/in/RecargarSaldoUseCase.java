package co.edu.udistrital.accionesud.cuenta.application.port.in;

import co.edu.udistrital.accionesud.cuenta.application.dto.EventoPago;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoRecarga;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Recarga de saldo con pasarela de pagos (SCRUM-46).
 *
 * <ol>
 *   <li>{@link #iniciar}: registra un depósito {@code PENDIENTE} (sin tocar el saldo) y devuelve la
 *   página de pago del proveedor.</li>
 *   <li>{@link #procesar}: con el aviso firmado del proveedor, completa el depósito y acredita el
 *   saldo una sola vez, o lo cancela si el pago no se hizo.</li>
 * </ol>
 */
public interface RecargarSaldoUseCase {

    ResultadoRecarga iniciar(UUID inversionistaId, BigDecimal monto, String claveIdempotencia);

    Procesamiento procesar(EventoPago evento);

    enum Procesamiento {
        /** El aviso cambió la recarga (acreditó o canceló). */
        APLICADO,
        /** El aviso ya se había procesado o la recarga ya estaba cerrada. */
        DUPLICADO,
        /** El aviso no corresponde a una recarga válida o no la cambia. */
        IGNORADO
    }
}
