package co.edu.udistrital.accionesud.cuenta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.udistrital.accionesud.cuenta.application.dto.EventoPago;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoRecarga;
import co.edu.udistrital.accionesud.cuenta.application.port.in.RecargarSaldoUseCase.Procesamiento;
import co.edu.udistrital.accionesud.cuenta.domain.exception.ConflictoIdempotenciaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoConfiguradaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoDisponibleException;
import co.edu.udistrital.accionesud.cuenta.domain.model.EstadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.pagos.PasarelaDeshabilitadaAdapter;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence.CuentaFondosEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.application.service.RegistradorAuditoria;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Recarga con pasarela (SCRUM-46) sobre el almacenamiento en memoria y una pasarela falsa. */
class RecargasServiceTest {

    private static final String TRACE = "trace-recarga";

    private CuentaFondosEnMemoriaAdapter fondos;
    private PasarelaFalsa pasarela;
    private FondosService saldos;
    private RecargasService recargas;
    private final List<EventoDominio> eventos = new CopyOnWriteArrayList<>();
    private UUID ana;

    @BeforeEach
    void preparar() {
        fondos = new CuentaFondosEnMemoriaAdapter();
        pasarela = new PasarelaFalsa();
        Clock reloj = Clock.fixed(Instant.parse("2026-11-26T18:00:00Z"), ZoneOffset.UTC);
        RegistradorAuditoria auditoria = new RegistradorAuditoria(new AuditoriaEnMemoriaAdapter(), () -> TRACE, reloj);
        saldos = new FondosService(fondos, new TransaccionLocalAdapter(), auditoria, eventos::add, () -> TRACE, reloj,
                new BigDecimal("100000.00"));
        recargas = new RecargasService(fondos, pasarela, saldos, new TransaccionLocalAdapter(), auditoria,
                eventos::add, () -> TRACE, reloj);
        ana = UUID.randomUUID();
    }

    private BigDecimal disponible() {
        return saldos.consultarSaldo(ana.toString()).getDisponible();
    }

    private static EventoPago pagado(MovimientoFondos m, String eventoId) {
        return new EventoPago("STRIPE", eventoId, EventoPago.Resultado.PAGADO, PasarelaFalsa.referenciaDe(m.id()),
                m.id(), m.monto().movePointRight(2).longValueExact(), "usd");
    }

    @Test
    void iniciarDejaElDepositoPendienteSinTocarElSaldoYDevuelveLaPaginaDePago() {
        ResultadoRecarga r = recargas.iniciar(ana, new BigDecimal("500.00"), "rec-1");

        assertThat(r.repetido()).isFalse();
        assertThat(r.movimiento().estado()).isEqualTo(EstadoMovimiento.PENDIENTE);
        assertThat(r.movimiento().completadoEn()).isNull();
        assertThat(r.urlPago()).isEqualTo("https://checkout.stripe.test/" + PasarelaFalsa.referenciaDe(r.movimiento().id()));
        assertThat(pasarela.solicitudes).singleElement().satisfies(s -> {
            assertThat(s.monto()).isEqualByComparingTo("500.00");
            assertThat(s.moneda()).isEqualTo("USD");
            assertThat(s.movimientoId()).isEqualTo(r.movimiento().id());
        });
        assertThat(disponible()).isEqualByComparingTo("100000.00");
        assertThat(eventos).isEmpty();
    }

    @Test
    void elAvisoDePagoAcreditaUnaSolaVezYPublicaElEvento() {
        MovimientoFondos m = recargas.iniciar(ana, new BigDecimal("500.00"), "rec-1").movimiento();

        assertThat(recargas.procesar(pagado(m, "evt_1"))).isEqualTo(Procesamiento.APLICADO);
        assertThat(recargas.procesar(pagado(m, "evt_1"))).isEqualTo(Procesamiento.DUPLICADO);
        // Otro evento distinto para la misma sesión (p. ej. async_payment_succeeded) tampoco duplica.
        assertThat(recargas.procesar(pagado(m, "evt_2"))).isEqualTo(Procesamiento.DUPLICADO);

        assertThat(disponible()).isEqualByComparingTo("100500.00");
        assertThat(fondos.buscarPorId(m.id())).get().satisfies(x -> {
            assertThat(x.estado()).isEqualTo(EstadoMovimiento.COMPLETADO);
            assertThat(x.completadoEn()).isNotNull();
        });
        assertThat(eventos).singleElement().isInstanceOfSatisfying(Eventos.MovimientoFondosRegistrado.class,
                e -> assertThat(e.saldoDisponible()).isEqualByComparingTo("100500.00"));
    }

    @Test
    void unMontoCobradoDistintoNoAcredita() {
        MovimientoFondos m = recargas.iniciar(ana, new BigDecimal("500.00"), "rec-1").movimiento();
        EventoPago menos = new EventoPago("STRIPE", "evt_1", EventoPago.Resultado.PAGADO,
                PasarelaFalsa.referenciaDe(m.id()), m.id(), 100, "usd");
        EventoPago otraMoneda = new EventoPago("STRIPE", "evt_2", EventoPago.Resultado.PAGADO,
                PasarelaFalsa.referenciaDe(m.id()), m.id(), 50000, "eur");

        assertThat(recargas.procesar(menos)).isEqualTo(Procesamiento.IGNORADO);
        assertThat(recargas.procesar(otraMoneda)).isEqualTo(Procesamiento.IGNORADO);
        assertThat(disponible()).isEqualByComparingTo("100000.00");
        assertThat(fondos.buscarPorId(m.id()).orElseThrow().estado()).isEqualTo(EstadoMovimiento.PENDIENTE);
    }

    @Test
    void unAvisoDeOtraSesionNoAcredita() {
        MovimientoFondos m = recargas.iniciar(ana, new BigDecimal("500.00"), "rec-1").movimiento();
        EventoPago ajeno = new EventoPago("STRIPE", "evt_1", EventoPago.Resultado.PAGADO, "cs_test_otra",
                m.id(), 50000, "usd");

        assertThat(recargas.procesar(ajeno)).isEqualTo(Procesamiento.IGNORADO);
        assertThat(disponible()).isEqualByComparingTo("100000.00");
    }

    @Test
    void siLaSesionVenceElDepositoQuedaCanceladoYYaNoSePuedeAcreditar() {
        MovimientoFondos m = recargas.iniciar(ana, new BigDecimal("500.00"), "rec-1").movimiento();
        EventoPago vencido = new EventoPago("STRIPE", "evt_exp", EventoPago.Resultado.NO_PAGADO,
                PasarelaFalsa.referenciaDe(m.id()), m.id(), 50000, "usd");

        assertThat(recargas.procesar(vencido)).isEqualTo(Procesamiento.APLICADO);
        assertThat(recargas.procesar(pagado(m, "evt_tarde"))).isEqualTo(Procesamiento.DUPLICADO);
        assertThat(fondos.buscarPorId(m.id()).orElseThrow().estado()).isEqualTo(EstadoMovimiento.CANCELADO);
        assertThat(disponible()).isEqualByComparingTo("100000.00");
    }

    @Test
    void siLaPasarelaFallaElDepositoQuedaFallidoYSeInformaElError() {
        pasarela.caer(true);

        assertThatThrownBy(() -> recargas.iniciar(ana, new BigDecimal("500.00"), "rec-1"))
                .isInstanceOf(PasarelaNoDisponibleException.class);
        MovimientoFondos m = saldos.listar(ana, null, 0, 20).contenido().stream()
                .filter(x -> x.monto().compareTo(new BigDecimal("500.00")) == 0).findFirst().orElseThrow();
        assertThat(m.estado()).isEqualTo(EstadoMovimiento.FALLIDO);
        assertThat(disponible()).isEqualByComparingTo("100000.00");
    }

    @Test
    void repetirLaClaveDevuelveLaMismaSesionMientrasEstaAbierta() {
        ResultadoRecarga primero = recargas.iniciar(ana, new BigDecimal("500.00"), "rec-1");
        ResultadoRecarga repetido = recargas.iniciar(ana, new BigDecimal("500"), "rec-1");

        assertThat(repetido.repetido()).isTrue();
        assertThat(repetido.movimiento().id()).isEqualTo(primero.movimiento().id());
        assertThat(repetido.urlPago()).isEqualTo(primero.urlPago());
        assertThat(pasarela.solicitudes).hasSize(1);

        pasarela.cerrar(PasarelaFalsa.referenciaDe(primero.movimiento().id()));
        assertThat(recargas.iniciar(ana, new BigDecimal("500"), "rec-1").urlPago()).isNull();
        assertThatThrownBy(() -> recargas.iniciar(ana, new BigDecimal("501"), "rec-1"))
                .isInstanceOf(ConflictoIdempotenciaException.class);
    }

    @Test
    void laClaveDeUnaRecargaNoChocaConLaDeUnDepositoDirecto() {
        saldos.depositar(ana, new BigDecimal("500.00"), "misma");

        ResultadoRecarga r = recargas.iniciar(ana, new BigDecimal("500.00"), "misma");

        assertThat(r.repetido()).isFalse();
        assertThat(r.movimiento().estado()).isEqualTo(EstadoMovimiento.PENDIENTE);
    }

    @Test
    void validaMontoMinimoYConfiguracion() {
        assertThatThrownBy(() -> recargas.iniciar(ana, new BigDecimal("0.49"), "k"))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> recargas.iniciar(ana, new BigDecimal("10"), null))
                .isInstanceOf(ValidacionException.class);

        RecargasService sinPasarela = new RecargasService(fondos, new PasarelaDeshabilitadaAdapter(), saldos,
                new TransaccionLocalAdapter(), new RegistradorAuditoria(new AuditoriaEnMemoriaAdapter(), () -> TRACE,
                Clock.systemUTC()), eventos::add, () -> TRACE, Clock.systemUTC());
        assertThatThrownBy(() -> sinPasarela.iniciar(ana, new BigDecimal("10"), "k"))
                .isInstanceOf(PasarelaNoConfiguradaException.class);
    }

    @Test
    void unAvisoIgnoradoOSinMovimientoNoHaceNada() {
        assertThat(recargas.procesar(new EventoPago("STRIPE", "evt_x", EventoPago.Resultado.IGNORADO, "cs", null, 0,
                "usd"))).isEqualTo(Procesamiento.IGNORADO);
        assertThat(recargas.procesar(new EventoPago("STRIPE", "evt_y", EventoPago.Resultado.PAGADO, "cs",
                UUID.randomUUID(), 100, "usd"))).isEqualTo(Procesamiento.IGNORADO);
    }
}
