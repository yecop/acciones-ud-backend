package co.edu.udistrital.accionesud.cuenta.application.port.in;

import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoMovimiento;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Depósitos y retiros simulados sobre la cuenta del inversionista (SCRUM-46).
 *
 * <p>La {@code claveIdempotencia} identifica la solicitud: repetirla con el mismo tipo y monto
 * devuelve el movimiento original sin aplicarlo otra vez.</p>
 */
public interface MoverFondosUseCase {

    ResultadoMovimiento depositar(UUID inversionistaId, BigDecimal monto, String claveIdempotencia);

    /**
     * @throws co.edu.udistrital.accionesud.cuenta.domain.exception.FondosInsuficientesException
     *         si el monto supera el disponible; el saldo no cambia y el intento queda registrado
     *         como {@code FALLIDO}.
     */
    ResultadoMovimiento retirar(UUID inversionistaId, BigDecimal monto, String claveIdempotencia);
}
