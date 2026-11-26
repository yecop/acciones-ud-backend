package co.edu.udistrital.accionesud.notificaciones.application.service;

import co.edu.udistrital.accionesud.notificaciones.application.dto.FiltroNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.dto.PaginaNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.domain.exception.NotificacionNoEncontradaException;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.CanalEnvioPort;
import co.edu.udistrital.accionesud.notificaciones.domain.exception.EnvioFallidoException;
import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Contacto;
import co.edu.udistrital.accionesud.notificaciones.domain.model.EnvioNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.TipoNotificacion;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.canal.CanalSimuladoAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia.NotificacionesEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.domain.evento.MetadatosEvento;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionLocalAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-51 con proveedores simulados: persistencia, idempotencia, reintentos y propiedad. */
class NotificacionesServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-25T15:00:00Z");
    private static final Duration ESPERA = Duration.ofSeconds(30);

    private final RelojMovil reloj = new RelojMovil(INICIO);
    private final NotificacionesEnMemoriaAdapter memoria = new NotificacionesEnMemoriaAdapter();
    private final CanalSimuladoAdapter email = new CanalSimuladoAdapter(CanalNotificacion.EMAIL);
    private final CanalSimuladoAdapter sms = new CanalSimuladoAdapter(CanalNotificacion.SMS);
    private NotificacionesService servicio;

    private final UUID usuario = UUID.randomUUID();
    private final UUID cuenta = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        servicio = new NotificacionesService(memoria, memoria, memoria, List.of(email, sms),
                new TransaccionLocalAdapter(),
                new NotificacionesService.PoliticaReintento(3, ESPERA, Duration.ofSeconds(120), 50), reloj);
        memoria.registrarCuenta(cuenta, usuario);
        memoria.registrarPreferencias(usuario, Set.of(CanalNotificacion.EMAIL, CanalNotificacion.SMS),
                Set.of("ORDEN_EJECUTADA"));
        memoria.registrarContacto(usuario, new Contacto("ana@example.test", "+573001112233"));
    }

    private Eventos.OrdenEjecutada ejecutada() {
        return new Eventos.OrdenEjecutada(MetadatosEvento.nuevo(reloj.instant(), "trace-ej", usuario),
                UUID.randomUUID(), cuenta, "MSFT", 3, new BigDecimal("410"), true);
    }

    private PaginaNotificaciones bandeja(UUID duenio) {
        return servicio.listar(new FiltroNotificaciones(duenio, null, null, null, 0, 20));
    }

    private EnvioNotificacion envio(Notificacion n, CanalNotificacion canal) {
        return memoria.buscar(n.id(), canal).orElseThrow();
    }

    @Test
    void unEventoSoportadoPersisteLaNotificacionYLaEnviaPorLosCanalesElegidos() {
        assertThat(servicio.procesar(ejecutada())).isEqualTo(1);

        Notificacion n = bandeja(usuario).contenido().get(0);
        assertThat(n.tipo()).isEqualTo(TipoNotificacion.ORDEN_EJECUTADA);
        assertThat(n.usuarioId()).isEqualTo(usuario);
        assertThat(n.traceId()).isEqualTo("trace-ej");
        assertThat(email.enviadas()).containsExactly(n.id());
        assertThat(sms.enviadas()).containsExactly(n.id());
        assertThat(envio(n, CanalNotificacion.EMAIL).estado()).isEqualTo(EnvioNotificacion.Estado.ENVIADO);
    }

    @Test
    void reprocesarElMismoEventoNoDuplicaNiReenvia() {
        EventoDominio evento = ejecutada();
        servicio.procesar(evento);
        assertThat(servicio.procesar(evento)).isZero();

        assertThat(bandeja(usuario).totalElementos()).isEqualTo(1);
        assertThat(email.intentos()).isEqualTo(1);
    }

    @Test
    void unFalloTemporalSeReintentaSinDuplicarLaNotificacion() {
        email.fallarProximos(1);
        servicio.procesar(ejecutada());
        Notificacion n = bandeja(usuario).contenido().get(0);

        EnvioNotificacion fallido = envio(n, CanalNotificacion.EMAIL);
        assertThat(fallido.estado()).isEqualTo(EnvioNotificacion.Estado.PENDIENTE);
        assertThat(fallido.intentos()).isEqualTo(1);
        assertThat(fallido.ultimoError()).contains("no disponible");
        assertThat(email.enviadas()).isEmpty();
        assertThat(sms.enviadas()).containsExactly(n.id()); // Un canal caído no afecta a los demás.

        assertThat(servicio.reintentarPendientes()).as("antes de la espera").isZero();

        reloj.avanzar(ESPERA);
        assertThat(servicio.reintentarPendientes()).isEqualTo(1);

        assertThat(envio(n, CanalNotificacion.EMAIL).estado()).isEqualTo(EnvioNotificacion.Estado.ENVIADO);
        assertThat(envio(n, CanalNotificacion.EMAIL).intentos()).isEqualTo(2);
        assertThat(email.enviadas()).containsExactly(n.id());
        assertThat(sms.enviadas()).containsExactly(n.id());
        assertThat(bandeja(usuario).totalElementos()).isEqualTo(1);
        assertThat(servicio.reintentarPendientes()).as("ya no queda nada").isZero();
    }

    @Test
    void alAgotarLosIntentosElEnvioQuedaFallidoPeroLaNotificacionSigueEnLaBandeja() {
        email.fallarProximos(10);
        servicio.procesar(ejecutada());
        Notificacion n = bandeja(usuario).contenido().get(0);

        reloj.avanzar(ESPERA);
        servicio.reintentarPendientes();
        reloj.avanzar(ESPERA.multipliedBy(2));
        servicio.reintentarPendientes();

        EnvioNotificacion envio = envio(n, CanalNotificacion.EMAIL);
        assertThat(envio.estado()).isEqualTo(EnvioNotificacion.Estado.FALLIDO);
        assertThat(envio.intentos()).isEqualTo(3);
        reloj.avanzar(Duration.ofHours(1));
        assertThat(servicio.reintentarPendientes()).isZero();
        assertThat(bandeja(usuario).contenido()).extracting(Notificacion::id).containsExactly(n.id());
    }

    @Test
    void unRechazoPermanenteQuedaFallidoSinReintentar() {
        CanalEnvioPort rechaza = new CanalEnvioPort() {
            @Override
            public CanalNotificacion canal() {
                return CanalNotificacion.EMAIL;
            }

            @Override
            public void enviar(Notificacion notificacion, Contacto contacto) {
                throw EnvioFallidoException.permanente("Dirección rechazada");
            }
        };
        servicio = new NotificacionesService(memoria, memoria, memoria, List.of(rechaza, sms),
                new TransaccionLocalAdapter(),
                new NotificacionesService.PoliticaReintento(3, ESPERA, Duration.ofSeconds(120), 50), reloj);

        servicio.procesar(ejecutada());
        Notificacion n = bandeja(usuario).contenido().get(0);

        EnvioNotificacion envio = envio(n, CanalNotificacion.EMAIL);
        assertThat(envio.estado()).isEqualTo(EnvioNotificacion.Estado.FALLIDO);
        assertThat(envio.intentos()).isEqualTo(1);
        assertThat(envio.ultimoError()).isEqualTo("Dirección rechazada");
        reloj.avanzar(Duration.ofHours(1));
        assertThat(servicio.reintentarPendientes()).isZero();
        assertThat(sms.enviadas()).containsExactly(n.id());
    }

    @Test
    void sinDatosDeContactoElEnvioQuedaFallido() {
        memoria.limpiar();
        memoria.registrarCuenta(cuenta, usuario);
        memoria.registrarPreferencias(usuario, Set.of(CanalNotificacion.EMAIL), Set.of("ORDEN_EJECUTADA"));
        servicio.procesar(ejecutada());

        Notificacion n = bandeja(usuario).contenido().get(0);
        assertThat(envio(n, CanalNotificacion.EMAIL).estado()).isEqualTo(EnvioNotificacion.Estado.FALLIDO);
        assertThat(email.intentos()).isZero();
    }

    @Test
    void sinPreferenciaDelEventoSoloQuedaEnLaBandeja() {
        memoria.registrarPreferencias(usuario, Set.of(CanalNotificacion.EMAIL), Set.of("APERTURA_CIERRE_MERCADO"));
        servicio.procesar(ejecutada());

        assertThat(bandeja(usuario).totalElementos()).isEqualTo(1);
        assertThat(email.intentos()).isZero();
    }

    @Test
    void losTiposFueraDelCatalogoNoSeEnvianPorCanalesExternos() {
        servicio.procesar(new Eventos.OrdenEstadoCambiado(MetadatosEvento.nuevo(INICIO, "t", null),
                UUID.randomUUID(), cuenta, "PENDIENTE", "CANCELADA", null));

        assertThat(bandeja(usuario).contenido()).extracting(Notificacion::tipo)
                .containsExactly(TipoNotificacion.ORDEN_CANCELADA);
        assertThat(email.intentos()).isZero();
        assertThat(sms.intentos()).isZero();
    }

    @Test
    void unaCuentaSinTitularNoCreaNotificaciones() {
        assertThat(servicio.procesar(new Eventos.OrdenEjecutada(MetadatosEvento.nuevo(INICIO, "t", null),
                UUID.randomUUID(), UUID.randomUUID(), "AAPL", 1, BigDecimal.ONE, true))).isZero();
    }

    @Test
    void elUsuarioSoloMarcaSusNotificaciones() {
        servicio.procesar(ejecutada());
        Notificacion n = bandeja(usuario).contenido().get(0);
        UUID otro = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.marcarLeida(otro, n.id()))
                .isInstanceOf(NotificacionNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.marcarLeida(usuario, UUID.randomUUID()))
                .isInstanceOf(NotificacionNoEncontradaException.class);
        assertThat(bandeja(otro).totalElementos()).isZero();
        assertThat(servicio.marcarTodasLeidas(otro)).isZero();

        Notificacion leida = servicio.marcarLeida(usuario, n.id());
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(servicio.marcarLeida(usuario, n.id()).leidaEn()).isEqualTo(leida.leidaEn());
    }

    @Test
    void filtraPorTipoFechaYLecturaYMarcaTodas() {
        servicio.procesar(ejecutada());
        reloj.avanzar(Duration.ofHours(2));
        servicio.procesar(new Eventos.OrdenEstadoCambiado(MetadatosEvento.nuevo(reloj.instant(), "t", null),
                UUID.randomUUID(), cuenta, "PENDIENTE", "CANCELADA", null));

        List<Notificacion> todas = bandeja(usuario).contenido();
        assertThat(todas).extracting(Notificacion::tipo)
                .containsExactly(TipoNotificacion.ORDEN_CANCELADA, TipoNotificacion.ORDEN_EJECUTADA);
        assertThat(servicio.listar(new FiltroNotificaciones(usuario, TipoNotificacion.ORDEN_EJECUTADA, null, null,
                0, 20)).totalElementos()).isEqualTo(1);
        assertThat(servicio.listar(new FiltroNotificaciones(usuario, null, INICIO.plusSeconds(60), null, 0, 20))
                .contenido()).extracting(Notificacion::tipo).containsExactly(TipoNotificacion.ORDEN_CANCELADA);

        PaginaNotificaciones primera = servicio.listar(new FiltroNotificaciones(usuario, null, null, null, 0, 1));
        assertThat(primera.totalPaginas()).isEqualTo(2);
        assertThat(primera.contenido()).hasSize(1);

        assertThat(servicio.marcarTodasLeidas(usuario)).isEqualTo(2);
        assertThat(servicio.marcarTodasLeidas(usuario)).isZero();
        assertThat(servicio.listar(new FiltroNotificaciones(usuario, null, null, false, 0, 20)).totalElementos())
                .isZero();
    }

    /** Reloj que la prueba avanza a voluntad. */
    static final class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
