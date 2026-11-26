package co.edu.udistrital.accionesud.cuenta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.udistrital.accionesud.cuenta.application.dto.PaginaMovimientos;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.domain.exception.ConflictoIdempotenciaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.FondosInsuficientesException;
import co.edu.udistrital.accionesud.cuenta.domain.model.EstadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.domain.model.TipoMovimiento;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence.CuentaFondosEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.application.service.RegistradorAuditoria;
import co.edu.udistrital.accionesud.shared.domain.auditoria.RegistroAuditoria;
import co.edu.udistrital.accionesud.shared.domain.auditoria.ResultadoAuditoria;
import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.auditoria.AuditoriaEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionLocalAdapter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas de SCRUM-46 (y el saldo inicial de SCRUM-25) sobre el almacenamiento en memoria. */
@DisplayName("FondosService")
class FondosServiceTest {

    private static final BigDecimal INICIAL = new BigDecimal("100000.00");
    private static final String TRACE = "trace-fondos";

    private CuentaFondosEnMemoriaAdapter fondos;
    private AuditoriaEnMemoriaAdapter auditoria;
    private final List<EventoDominio> eventos = new CopyOnWriteArrayList<>();
    private FondosService servicio;
    private UUID ana;

    @BeforeEach
    void preparar() {
        fondos = new CuentaFondosEnMemoriaAdapter();
        auditoria = new AuditoriaEnMemoriaAdapter();
        Clock reloj = Clock.fixed(Instant.parse("2026-11-26T15:00:00Z"), ZoneOffset.UTC);
        servicio = new FondosService(fondos, new TransaccionLocalAdapter(),
                new RegistradorAuditoria(auditoria, () -> TRACE, reloj), eventos::add, () -> TRACE, reloj, INICIAL);
        ana = UUID.randomUUID();
    }

    private Saldo saldo() {
        return servicio.consultarSaldo(ana.toString());
    }

    @Test
    void laPrimeraConsultaAplicaElSaldoInicialComoUnDepositoVisibleEnElHistorial() {
        assertThat(saldo().getDisponible()).isEqualByComparingTo(INICIAL);
        assertThat(saldo().getDisponible()).isEqualByComparingTo(INICIAL); // no se duplica

        PaginaMovimientos historial = servicio.listar(ana, null, 0, 20);
        assertThat(historial.totalElementos()).isEqualTo(1);
        assertThat(historial.contenido().get(0).tipo()).isEqualTo(TipoMovimiento.DEPOSITO);
        assertThat(historial.contenido().get(0).monto()).isEqualByComparingTo(INICIAL);
    }

    @Test
    void unDepositoAumentaElDisponibleYQuedaRegistradoConTraceIdAuditoriaYEvento() {
        ResultadoMovimiento r = servicio.depositar(ana, new BigDecimal("250.50"), "dep-1");

        assertThat(r.repetido()).isFalse();
        assertThat(r.movimiento().estado()).isEqualTo(EstadoMovimiento.COMPLETADO);
        assertThat(r.movimiento().traceId()).isEqualTo(TRACE);
        assertThat(r.movimiento().completadoEn()).isNotNull();
        assertThat(r.saldo().getDisponible()).isEqualByComparingTo("100250.50");

        assertThat(auditoria.registros()).extracting(RegistroAuditoria::accion)
                .containsExactly("SALDO_INICIAL", "DEPOSITO");
        assertThat(eventos).singleElement().isInstanceOfSatisfying(Eventos.MovimientoFondosRegistrado.class, e -> {
            assertThat(e.tipo()).isEqualTo("DEPOSITO");
            assertThat(e.saldoDisponible()).isEqualByComparingTo("100250.50");
            assertThat(e.metadatos().traceId()).isEqualTo(TRACE);
        });
    }

    @Test
    void unRetiroMayorAlSaldoSeRechazaSinModificarLaCuentaYQuedaComoFallido() {
        assertThatThrownBy(() -> servicio.retirar(ana, new BigDecimal("100000.01"), "ret-1"))
                .isInstanceOf(FondosInsuficientesException.class)
                .hasFieldOrPropertyWithValue("codigo", "FONDOS_INSUFICIENTES");

        assertThat(saldo().getDisponible()).isEqualByComparingTo(INICIAL);
        PaginaMovimientos retiros = servicio.listar(ana, TipoMovimiento.RETIRO, 0, 20);
        assertThat(retiros.contenido()).singleElement().satisfies(m -> {
            assertThat(m.estado()).isEqualTo(EstadoMovimiento.FALLIDO);
            assertThat(m.completadoEn()).isNull();
            assertThat(m.traceId()).isEqualTo(TRACE);
        });
        assertThat(auditoria.registros()).filteredOn(a -> a.accion().equals("RETIRO"))
                .extracting(RegistroAuditoria::resultado).containsExactly(ResultadoAuditoria.RECHAZADO);
        assertThat(eventos).isEmpty();
    }

    @Test
    void unRetiroQueAlcanzaDescuentaDelDisponible() {
        ResultadoMovimiento r = servicio.retirar(ana, new BigDecimal("100000.00"), "ret-todo");

        assertThat(r.saldo().getDisponible()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> servicio.retirar(ana, new BigDecimal("0.01"), "ret-mas"))
                .isInstanceOf(FondosInsuficientesException.class);
    }

    @Test
    void repetirLaClaveDevuelveElMovimientoOriginalSinAplicarloOtraVez() {
        ResultadoMovimiento primero = servicio.depositar(ana, new BigDecimal("10"), "dep-x");
        ResultadoMovimiento repetido = servicio.depositar(ana, new BigDecimal("10.00"), "dep-x");

        assertThat(repetido.repetido()).isTrue();
        assertThat(repetido.movimiento().id()).isEqualTo(primero.movimiento().id());
        assertThat(saldo().getDisponible()).isEqualByComparingTo("100010.00");
    }

    @Test
    void repetirLaClaveConOtrosDatosEsUnConflicto() {
        servicio.depositar(ana, new BigDecimal("10"), "clave");

        assertThatThrownBy(() -> servicio.depositar(ana, new BigDecimal("11"), "clave"))
                .isInstanceOf(ConflictoIdempotenciaException.class);
        assertThatThrownBy(() -> servicio.retirar(ana, new BigDecimal("10"), "clave"))
                .isInstanceOf(ConflictoIdempotenciaException.class);
    }

    @Test
    void repetirUnRetiroRechazadoVuelveARechazarlo() {
        assertThatThrownBy(() -> servicio.retirar(ana, new BigDecimal("999999"), "r"))
                .isInstanceOf(FondosInsuficientesException.class);
        assertThatThrownBy(() -> servicio.retirar(ana, new BigDecimal("999999"), "r"))
                .isInstanceOf(FondosInsuficientesException.class);
        assertThat(servicio.listar(ana, TipoMovimiento.RETIRO, 0, 20).totalElementos()).isEqualTo(1);
    }

    @Test
    void laMismaClaveEnOtraCuentaEsOtraOperacion() {
        UUID beto = UUID.randomUUID();
        servicio.depositar(ana, new BigDecimal("5"), "comun");

        ResultadoMovimiento deBeto = servicio.depositar(beto, new BigDecimal("7"), "comun");

        assertThat(deBeto.repetido()).isFalse();
        assertThat(deBeto.saldo().getDisponible()).isEqualByComparingTo("100007.00");
    }

    @Test
    void validaMontoYClave() {
        assertThatThrownBy(() -> servicio.depositar(ana, BigDecimal.ZERO, "k"))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.depositar(ana, new BigDecimal("-5"), "k"))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.depositar(ana, new BigDecimal("1.001"), "k"))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.depositar(ana, new BigDecimal("1000000.01"), "k"))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.depositar(ana, null, "k"))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.depositar(ana, BigDecimal.ONE, " "))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.depositar(ana, BigDecimal.ONE, "x".repeat(61)))
                .isInstanceOf(ValidacionException.class);
        assertThat(servicio.depositar(ana, new BigDecimal("1.10"), "k").movimiento().monto())
                .isEqualByComparingTo("1.10");
    }

    @Test
    void retirosConcurrentesNuncaDejanElSaldoNegativo() throws Exception {
        saldo(); // 100000.00
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> intentos = new java.util.ArrayList<>();
            for (int i = 0; i < 25; i++) {
                String clave = "ret-" + i;
                intentos.add(hilos.submit(() -> {
                    try {
                        servicio.retirar(ana, new BigDecimal("10000.00"), clave);
                        return true;
                    } catch (FondosInsuficientesException e) {
                        return false;
                    }
                }));
            }
            long exitosos = 0;
            for (Future<Boolean> f : intentos) {
                exitosos += f.get() ? 1 : 0;
            }
            assertThat(exitosos).isEqualTo(10);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(saldo().getDisponible()).isEqualByComparingTo("0");
    }
}
