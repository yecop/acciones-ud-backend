package co.edu.udistrital.accionesud.cuenta.application.service;

import co.edu.udistrital.accionesud.cuenta.application.dto.EventoPago;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoRecarga;
import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.in.RecargarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.out.CuentaFondosPort;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort.SesionPago;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort.SolicitudPago;
import co.edu.udistrital.accionesud.cuenta.domain.exception.ConflictoIdempotenciaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.CuentaNoEncontradaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoConfiguradaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoDisponibleException;
import co.edu.udistrital.accionesud.cuenta.domain.model.EstadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Recarga de saldo con pasarela de pagos (SCRUM-46).
 *
 * <ul>
 *   <li><b>Inicio:</b> registra un {@code DEPOSITO} {@code PENDIENTE} sin tocar el saldo y pide a la
 *   pasarela la página de pago. Si la pasarela falla, el depósito queda {@code FALLIDO}.</li>
 *   <li><b>Confirmación:</b> el saldo solo se acredita con el aviso firmado de la pasarela, nunca
 *   porque el navegador vuelva a la página de éxito. El aviso se procesa una vez
 *   ({@code evento_pago_procesado}) y el paso {@code PENDIENTE → COMPLETADO} es condicional, así
 *   que avisos repetidos o simultáneos no acreditan dos veces.</li>
 *   <li><b>Verificación:</b> se acredita solo si el aviso corresponde a la sesión guardada y el monto
 *   y la moneda cobrados coinciden con los del depósito.</li>
 * </ul>
 */
public class RecargasService implements RecargarSaldoUseCase {

    /** Stripe no cobra menos de 0.50 USD. */
    public static final BigDecimal MONTO_MINIMO = new BigDecimal("0.50");
    private static final String MONEDA = FondosService.MONEDA;

    private final CuentaFondosPort fondos;
    private final PasarelaPagosPort pasarela;
    private final ConsultarSaldoUseCase saldos;
    private final TransaccionPort transaccion;
    private final RegistradorAuditoria auditoria;
    private final PublicadorEventosPort eventos;
    private final TrazabilidadPort trazabilidad;
    private final Clock reloj;

    public RecargasService(CuentaFondosPort fondos, PasarelaPagosPort pasarela, ConsultarSaldoUseCase saldos,
                           TransaccionPort transaccion, RegistradorAuditoria auditoria,
                           PublicadorEventosPort eventos, TrazabilidadPort trazabilidad, Clock reloj) {
        this.fondos = Objects.requireNonNull(fondos);
        this.pasarela = Objects.requireNonNull(pasarela);
        this.saldos = Objects.requireNonNull(saldos);
        this.transaccion = Objects.requireNonNull(transaccion);
        this.auditoria = Objects.requireNonNull(auditoria);
        this.eventos = Objects.requireNonNull(eventos);
        this.trazabilidad = Objects.requireNonNull(trazabilidad);
        this.reloj = Objects.requireNonNull(reloj);
    }

    // ------------------------------------------------------------------ inicio

    @Override
    public ResultadoRecarga iniciar(UUID inversionistaId, BigDecimal monto, String clave) {
        Objects.requireNonNull(inversionistaId, "El inversionista es obligatorio");
        FondosService.validar(monto, clave);
        if (monto.compareTo(MONTO_MINIMO) < 0) {
            throw new ValidacionException("monto", "la recarga mínima es " + MONTO_MINIMO.toPlainString());
        }
        if (!pasarela.configurada()) {
            throw new PasarelaNoConfiguradaException();
        }
        // Asegura la cuenta y su saldo inicial antes del primer movimiento propio.
        saldos.consultarSaldo(inversionistaId.toString());
        UUID cuenta = fondos.cuentaDe(inversionistaId).orElseThrow(CuentaNoEncontradaException::new);
        // Prefijo propio: la clave de una recarga no choca con la de un depósito directo.
        String claveCuenta = cuenta + ":r:" + clave.strip();

        Optional<MovimientoFondos> previo = fondos.buscarPorClave(claveCuenta);
        if (previo.isPresent()) {
            return repetir(previo.get(), monto);
        }

        Instant ahora = reloj.instant();
        MovimientoFondos pendiente = new MovimientoFondos(UUID.randomUUID(), cuenta, TipoMovimiento.DEPOSITO,
                EstadoMovimiento.PENDIENTE, monto, MONEDA, ahora, null, trazabilidad.traceIdActual());
        try {
            transaccion.enTransaccion(() -> {
                fondos.registrar(pendiente, claveCuenta);
                auditoria.exitoso(inversionistaId, "RECARGA_INICIADA", "CUENTA", cuenta.toString(),
                        Map.of("movimientoId", pendiente.id().toString(), "monto", monto.toPlainString()));
            });
        } catch (ConflictoIdempotenciaException carrera) {
            MovimientoFondos ganador = fondos.buscarPorClave(claveCuenta).orElseThrow(() -> carrera);
            return repetir(ganador, monto);
        }

        SesionPago sesion;
        try {
            sesion = pasarela.crearSesion(new SolicitudPago(pendiente.id(), cuenta, monto, MONEDA));
        } catch (PasarelaNoDisponibleException | PasarelaNoConfiguradaException e) {
            transaccion.enTransaccion(() -> {
                fondos.cerrarPendiente(pendiente.id(), EstadoMovimiento.FALLIDO);
                auditoria.registrar(inversionistaId, "RECARGA_INICIADA", "CUENTA", cuenta.toString(),
                        ResultadoAuditoria.FALLIDO, Map.of("movimientoId", pendiente.id().toString(),
                                "motivo", e.getCodigo()));
            });
            throw e;
        }
        fondos.asignarReferencia(pendiente.id(), sesion.referencia());
        return new ResultadoRecarga(pendiente, sesion.url(), false);
    }

    private ResultadoRecarga repetir(MovimientoFondos previo, BigDecimal monto) {
        if (!previo.mismaOperacion(TipoMovimiento.DEPOSITO, monto)) {
            throw new ConflictoIdempotenciaException();
        }
        String url = null;
        if (previo.estado() == EstadoMovimiento.PENDIENTE) {
            url = fondos.referenciaDe(previo.id())
                    .flatMap(pasarela::consultarSesion)
                    .filter(SesionPago::abierta)
                    .map(SesionPago::url)
                    .orElse(null);
        }
        return new ResultadoRecarga(previo, url, true);
    }

    // ------------------------------------------------------------------ aviso de la pasarela

    @Override
    public Procesamiento procesar(EventoPago evento) {
        Objects.requireNonNull(evento);
        if (evento.resultado() == EventoPago.Resultado.IGNORADO || evento.movimientoId() == null) {
            return Procesamiento.IGNORADO;
        }
        return transaccion.enTransaccion(() -> {
            if (!fondos.registrarEventoPago(evento.proveedor(), evento.eventoId())) {
                return Procesamiento.DUPLICADO;
            }
            Optional<MovimientoFondos> encontrado = fondos.buscarPorId(evento.movimientoId());
            if (encontrado.isEmpty() || encontrado.get().tipo() != TipoMovimiento.DEPOSITO) {
                return Procesamiento.IGNORADO;
            }
            MovimientoFondos movimiento = encontrado.get();
            String cuenta = movimiento.cuentaId().toString();
            Optional<String> referencia = fondos.referenciaDe(movimiento.id());
            if (referencia.isPresent() && !referencia.get().equals(evento.referencia())) {
                rechazar(cuenta, movimiento, "La sesión de pago no corresponde al depósito");
                return Procesamiento.IGNORADO;
            }

            if (evento.resultado() == EventoPago.Resultado.NO_PAGADO) {
                if (!fondos.cerrarPendiente(movimiento.id(), EstadoMovimiento.CANCELADO)) {
                    return Procesamiento.DUPLICADO;
                }
                auditoria.registrar(null, "RECARGA", "CUENTA", cuenta, ResultadoAuditoria.RECHAZADO,
                        Map.of("movimientoId", movimiento.id().toString(), "estado", "CANCELADO"));
                return Procesamiento.APLICADO;
            }

            if (!coincideMonto(movimiento, evento)) {
                rechazar(cuenta, movimiento, "El monto cobrado no coincide con el depósito");
                return Procesamiento.IGNORADO;
            }
            if (!fondos.completarPendiente(movimiento.id(), reloj.instant())) {
                return Procesamiento.DUPLICADO;
            }
            BigDecimal disponible = fondos.acreditar(movimiento.cuentaId(), movimiento.monto());
            auditoria.exitoso(null, "RECARGA", "CUENTA", cuenta,
                    Map.of("movimientoId", movimiento.id().toString(), "monto", movimiento.monto().toPlainString(),
                            "referencia", evento.referencia() == null ? "" : evento.referencia()));
            eventos.publicar(new Eventos.MovimientoFondosRegistrado(auditoria.metadatosEvento(null),
                    movimiento.cuentaId(), TipoMovimiento.DEPOSITO.name(), movimiento.monto(), disponible));
            return Procesamiento.APLICADO;
        });
    }

    private static boolean coincideMonto(MovimientoFondos movimiento, EventoPago evento) {
        long esperado = movimiento.monto().movePointRight(2).longValueExact();
        return evento.montoCentavos() == esperado
                && evento.moneda() != null
                && evento.moneda().toUpperCase(Locale.ROOT).equals(movimiento.moneda());
    }

    private void rechazar(String cuenta, MovimientoFondos movimiento, String motivo) {
        auditoria.registrar(null, "RECARGA", "CUENTA", cuenta, ResultadoAuditoria.RECHAZADO,
                Map.of("movimientoId", movimiento.id().toString(), "motivo", motivo));
    }
}
