package co.edu.udistrital.accionesud.cuenta.application.service;

import co.edu.udistrital.accionesud.cuenta.application.dto.PaginaMovimientos;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarMovimientosUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.in.MoverFondosUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.out.CuentaFondosPort;
import co.edu.udistrital.accionesud.cuenta.domain.exception.ConflictoIdempotenciaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.CuentaNoEncontradaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.FondosInsuficientesException;
import co.edu.udistrital.accionesud.cuenta.domain.model.EstadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.domain.model.TipoMovimiento;
import co.edu.udistrital.accionesud.shared.application.port.out.PublicadorEventosPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TrazabilidadPort;
import co.edu.udistrital.accionesud.shared.application.service.RegistradorAuditoria;
import co.edu.udistrital.accionesud.shared.domain.auditoria.ResultadoAuditoria;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Saldo y movimientos de fondos de la cuenta (SCRUM-25 y SCRUM-46).
 *
 * <ul>
 *   <li><b>Saldo inicial simulado:</b> la primera vez que se usa una cuenta sin movimientos se le
 *   aplica el saldo inicial configurado como un depósito, que aparece en el historial.</li>
 *   <li><b>Atomicidad:</b> el cambio de saldo, el registro del movimiento, la auditoría y el evento
 *   van en una sola transacción. El débito es condicional en el almacenamiento, así que dos
 *   retiros concurrentes nunca dejan el saldo negativo.</li>
 *   <li><b>Retiro sin fondos:</b> no modifica el saldo; el intento queda registrado como
 *   {@code FALLIDO} con su traceId y se responde {@code FONDOS_INSUFICIENTES}.</li>
 *   <li><b>Idempotencia:</b> la clave es única por cuenta. Repetirla con el mismo tipo y monto
 *   devuelve el movimiento original; con otros datos responde {@code CONFLICTO_IDEMPOTENCIA}.</li>
 * </ul>
 */
public class FondosService implements MoverFondosUseCase, ConsultarMovimientosUseCase, ConsultarSaldoUseCase {

    public static final String MONEDA = "USD";
    public static final BigDecimal MONTO_MAXIMO = new BigDecimal("1000000.00");
    public static final int LARGO_MAXIMO_CLAVE = 60;
    static final String CLAVE_SALDO_INICIAL = "saldo-inicial";

    private final CuentaFondosPort fondos;
    private final TransaccionPort transaccion;
    private final RegistradorAuditoria auditoria;
    private final PublicadorEventosPort eventos;
    private final TrazabilidadPort trazabilidad;
    private final Clock reloj;
    private final BigDecimal saldoInicialSimulado;

    public FondosService(CuentaFondosPort fondos, TransaccionPort transaccion, RegistradorAuditoria auditoria,
                         PublicadorEventosPort eventos, TrazabilidadPort trazabilidad, Clock reloj,
                         BigDecimal saldoInicialSimulado) {
        this.fondos = Objects.requireNonNull(fondos);
        this.transaccion = Objects.requireNonNull(transaccion);
        this.auditoria = Objects.requireNonNull(auditoria);
        this.eventos = Objects.requireNonNull(eventos);
        this.trazabilidad = Objects.requireNonNull(trazabilidad);
        this.reloj = Objects.requireNonNull(reloj);
        this.saldoInicialSimulado = Objects.requireNonNull(saldoInicialSimulado);
        if (saldoInicialSimulado.signum() < 0) {
            throw new IllegalArgumentException("El saldo inicial simulado no puede ser negativo");
        }
    }

    // ------------------------------------------------------------------ saldo

    @Override
    public Saldo consultarSaldo(String usuarioId) {
        Objects.requireNonNull(usuarioId, "El usuarioId no puede ser nulo");
        UUID cuenta = cuentaDe(interpretar(usuarioId));
        transaccion.enTransaccion(() -> asegurarSaldoInicial(cuenta));
        return fondos.saldo(cuenta);
    }

    // ------------------------------------------------------------------ movimientos

    @Override
    public ResultadoMovimiento depositar(UUID inversionistaId, BigDecimal monto, String claveIdempotencia) {
        return mover(inversionistaId, TipoMovimiento.DEPOSITO, monto, claveIdempotencia);
    }

    @Override
    public ResultadoMovimiento retirar(UUID inversionistaId, BigDecimal monto, String claveIdempotencia) {
        return mover(inversionistaId, TipoMovimiento.RETIRO, monto, claveIdempotencia);
    }

    @Override
    public PaginaMovimientos listar(UUID inversionistaId, TipoMovimiento tipo, int pagina, int tamano) {
        List<ValidacionException.ErrorCampo> errores = new ArrayList<>();
        if (pagina < 0) {
            errores.add(new ValidacionException.ErrorCampo("pagina", "debe ser mayor o igual a 0"));
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            errores.add(new ValidacionException.ErrorCampo("tamano", "debe estar entre 1 y " + TAMANO_MAXIMO));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        UUID cuenta = cuentaDe(inversionistaId);
        transaccion.enTransaccion(() -> asegurarSaldoInicial(cuenta));
        return fondos.listar(cuenta, tipo, pagina, tamano);
    }

    private ResultadoMovimiento mover(UUID inversionistaId, TipoMovimiento tipo, BigDecimal monto, String clave) {
        Objects.requireNonNull(inversionistaId, "El inversionista es obligatorio");
        validar(monto, clave);
        UUID cuenta = cuentaDe(inversionistaId);
        String claveCuenta = cuenta + ":" + clave.strip();

        Optional<MovimientoFondos> previo = fondos.buscarPorClave(claveCuenta);
        if (previo.isPresent()) {
            return repetir(previo.get(), tipo, monto);
        }

        MovimientoFondos movimiento;
        try {
            movimiento = transaccion.enTransaccion(() -> aplicar(inversionistaId, cuenta, tipo, monto, claveCuenta));
        } catch (ConflictoIdempotenciaException carrera) {
            // Otra solicitud con la misma clave ganó la carrera: se responde como una repetición.
            MovimientoFondos ganador = fondos.buscarPorClave(claveCuenta).orElseThrow(() -> carrera);
            return repetir(ganador, tipo, monto);
        }
        if (movimiento.estado() == EstadoMovimiento.FALLIDO) {
            throw new FondosInsuficientesException();
        }
        return new ResultadoMovimiento(movimiento, fondos.saldo(cuenta), false);
    }

    private MovimientoFondos aplicar(UUID actor, UUID cuenta, TipoMovimiento tipo, BigDecimal monto,
                                     String claveCuenta) {
        asegurarSaldoInicial(cuenta);
        Optional<BigDecimal> disponible = tipo.acredita()
                ? Optional.of(fondos.acreditar(cuenta, monto))
                : fondos.debitar(cuenta, monto);

        Instant ahora = reloj.instant();
        boolean aplicado = disponible.isPresent();
        MovimientoFondos movimiento = new MovimientoFondos(UUID.randomUUID(), cuenta, tipo,
                aplicado ? EstadoMovimiento.COMPLETADO : EstadoMovimiento.FALLIDO, monto, MONEDA, ahora,
                aplicado ? ahora : null, trazabilidad.traceIdActual());
        fondos.registrar(movimiento, claveCuenta);

        auditoria.registrar(actor, tipo.name(), "CUENTA", cuenta.toString(),
                aplicado ? ResultadoAuditoria.EXITOSO : ResultadoAuditoria.RECHAZADO,
                Map.of("movimientoId", movimiento.id().toString(), "monto", monto.toPlainString(),
                        "estado", movimiento.estado().name()));
        if (aplicado) {
            eventos.publicar(new Eventos.MovimientoFondosRegistrado(auditoria.metadatosEvento(actor), cuenta,
                    tipo.name(), monto, disponible.get()));
        }
        return movimiento;
    }

    /**
     * Aplica el saldo inicial simulado si la cuenta no tiene movimientos (debe ir en transacción).
     *
     * <p>Se registra y se audita, pero a propósito no publica {@code MovimientoFondosRegistrado}:
     * ese evento crea la notificación {@code MOVIMIENTO_FONDOS} y el usuario recibiría el aviso de
     * un depósito que él no hizo.</p>
     */
    private void asegurarSaldoInicial(UUID cuenta) {
        if (saldoInicialSimulado.signum() == 0) {
            return;
        }
        Instant ahora = reloj.instant();
        MovimientoFondos inicial = new MovimientoFondos(UUID.randomUUID(), cuenta, TipoMovimiento.DEPOSITO,
                EstadoMovimiento.COMPLETADO, saldoInicialSimulado, MONEDA, ahora, ahora,
                trazabilidad.traceIdActual());
        if (fondos.aplicarSaldoInicial(inicial, CLAVE_SALDO_INICIAL + ":" + cuenta)) {
            auditoria.exitoso(null, "SALDO_INICIAL", "CUENTA", cuenta.toString(),
                    Map.of("movimientoId", inicial.id().toString(), "monto", saldoInicialSimulado.toPlainString()));
        }
    }

    private ResultadoMovimiento repetir(MovimientoFondos previo, TipoMovimiento tipo, BigDecimal monto) {
        if (!previo.mismaOperacion(tipo, monto)) {
            throw new ConflictoIdempotenciaException();
        }
        if (previo.estado() == EstadoMovimiento.FALLIDO) {
            throw new FondosInsuficientesException();
        }
        return new ResultadoMovimiento(previo, fondos.saldo(previo.cuentaId()), true);
    }

    private UUID cuentaDe(UUID inversionistaId) {
        return fondos.cuentaDe(inversionistaId).orElseThrow(CuentaNoEncontradaException::new);
    }

    private static UUID interpretar(String usuarioId) {
        try {
            return UUID.fromString(usuarioId);
        } catch (IllegalArgumentException e) {
            throw new CuentaNoEncontradaException();
        }
    }

    /** Reglas comunes del monto y la clave de idempotencia (también las usa la recarga con pasarela). */
    static void validar(BigDecimal monto, String clave) {
        List<ValidacionException.ErrorCampo> errores = new ArrayList<>();
        if (monto == null) {
            errores.add(new ValidacionException.ErrorCampo("monto", "es obligatorio"));
        } else if (monto.signum() <= 0) {
            errores.add(new ValidacionException.ErrorCampo("monto", "debe ser mayor que 0"));
        } else if (monto.stripTrailingZeros().scale() > 2) {
            errores.add(new ValidacionException.ErrorCampo("monto", "admite como máximo 2 decimales"));
        } else if (monto.compareTo(MONTO_MAXIMO) > 0) {
            errores.add(new ValidacionException.ErrorCampo("monto",
                    "no puede superar " + MONTO_MAXIMO.toPlainString()));
        }
        if (clave == null || clave.isBlank()) {
            errores.add(new ValidacionException.ErrorCampo("Idempotency-Key", "es obligatoria"));
        } else if (clave.strip().length() > LARGO_MAXIMO_CLAVE) {
            errores.add(new ValidacionException.ErrorCampo("Idempotency-Key",
                    "no puede superar " + LARGO_MAXIMO_CLAVE + " caracteres"));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
    }
}
