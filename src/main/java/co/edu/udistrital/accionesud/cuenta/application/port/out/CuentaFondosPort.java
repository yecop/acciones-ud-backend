package co.edu.udistrital.accionesud.cuenta.application.port.out;

import co.edu.udistrital.accionesud.cuenta.application.dto.PaginaMovimientos;
import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.domain.model.TipoMovimiento;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia del saldo y de los movimientos de fondos de una cuenta (SCRUM-46).
 *
 * <p>Los cambios de saldo son atómicos en el propio almacenamiento ({@link #acreditar} y
 * {@link #debitar}), así que dos operaciones concurrentes nunca dejan un saldo incorrecto ni
 * negativo. Los métodos que escriben se llaman dentro de un {@code TransaccionPort}.</p>
 */
public interface CuentaFondosPort {

    /** Cuenta de corretaje del inversionista, si tiene. */
    Optional<UUID> cuentaDe(UUID inversionistaId);

    /** Saldo actual de la cuenta. */
    Saldo saldo(UUID cuentaId);

    /**
     * Aplica el saldo inicial simulado (SCRUM-25) como un depósito, solo si la cuenta todavía no
     * tiene ningún movimiento. Es seguro llamarlo varias veces y en paralelo: se aplica una vez.
     *
     * @return {@code true} si se aplicó en esta llamada.
     */
    boolean aplicarSaldoInicial(MovimientoFondos deposito, String claveIdempotencia);

    /** Suma al disponible. @return el disponible resultante. */
    BigDecimal acreditar(UUID cuentaId, BigDecimal monto);

    /**
     * Resta del disponible solo si alcanza, en una única operación atómica.
     *
     * @return el disponible resultante, o vacío si no alcanzaba (el saldo no cambia).
     */
    Optional<BigDecimal> debitar(UUID cuentaId, BigDecimal monto);

    /**
     * Registra el movimiento con su clave de idempotencia (única).
     *
     * @throws co.edu.udistrital.accionesud.cuenta.domain.exception.ConflictoIdempotenciaException
     *         si otra solicitud ya registró esa clave.
     */
    void registrar(MovimientoFondos movimiento, String claveIdempotencia);

    /** Movimiento registrado con esa clave de idempotencia, si existe. */
    Optional<MovimientoFondos> buscarPorClave(String claveIdempotencia);

    /** Movimientos de la cuenta, más recientes primero; {@code tipo} nulo = todos. */
    PaginaMovimientos listar(UUID cuentaId, TipoMovimiento tipo, int pagina, int tamano);
}
