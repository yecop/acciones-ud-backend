package co.edu.udistrital.accionesud.cuenta.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.ConflictoException;

/**
 * La {@code Idempotency-Key} ya se usó en esta cuenta para otra operación (otro tipo o monto), o
 * otra solicitud con la misma clave la registró al mismo tiempo. Responde 409
 * {@code CONFLICTO_IDEMPOTENCIA}.
 */
public class ConflictoIdempotenciaException extends ConflictoException {

    public ConflictoIdempotenciaException() {
        super("CONFLICTO_IDEMPOTENCIA", "La clave de idempotencia ya se usó con datos distintos");
    }
}
