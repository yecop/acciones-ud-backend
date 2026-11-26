package co.edu.udistrital.accionesud.cuenta.application.port.out;

import co.edu.udistrital.accionesud.cuenta.application.dto.PaginaMovimientos;
import co.edu.udistrital.accionesud.cuenta.domain.model.EstadoMovimiento;
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

    // ------------------------------------------------------------ recarga con pasarela

    /** Movimiento por su id, si existe. */
    Optional<MovimientoFondos> buscarPorId(UUID movimientoId);

    /** Guarda el id del pago en la pasarela ({@code referencia_externa}). */
    void asignarReferencia(UUID movimientoId, String referencia);

    /** Id del pago en la pasarela, si ya se asignó. */
    Optional<String> referenciaDe(UUID movimientoId);

    /**
     * Pasa un movimiento de {@code PENDIENTE} a {@code COMPLETADO}. Es condicional: si ya no estaba
     * pendiente no cambia nada, así un aviso repetido o concurrente no acredita dos veces.
     *
     * @return {@code true} si lo completó esta llamada.
     */
    boolean completarPendiente(UUID movimientoId, java.time.Instant completadoEn);

    /** Pasa un movimiento {@code PENDIENTE} a {@code FALLIDO} o {@code CANCELADO}; condicional como el anterior. */
    boolean cerrarPendiente(UUID movimientoId, EstadoMovimiento estado);

    /**
     * Marca un aviso de la pasarela como procesado ({@code evento_pago_procesado}).
     *
     * @return {@code false} si ya se había procesado (o lo está procesando otra solicitud).
     */
    boolean registrarEventoPago(String proveedor, String eventoId);
}
