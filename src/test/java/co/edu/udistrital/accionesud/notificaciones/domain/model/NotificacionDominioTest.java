package co.edu.udistrital.accionesud.notificaciones.domain.model;

import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.domain.evento.MetadatosEvento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificacionDominioTest {

    private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");
    private static final MetadatosEvento META = MetadatosEvento.nuevo(AHORA, "trace-1", null);
    private final TraductorEventos traductor = new TraductorEventos();

    @Test
    void elIdSeDerivaDelEventoUsuarioYTipo() {
        UUID usuario = UUID.randomUUID();
        UUID evento = UUID.randomUUID();
        Notificacion a = Notificacion.nueva(usuario, evento, TipoNotificacion.ORDEN_EJECUTADA, "t", "m", null, AHORA, "x");
        Notificacion b = Notificacion.nueva(usuario, evento, TipoNotificacion.ORDEN_EJECUTADA, "t", "m", null, AHORA, "x");
        Notificacion otroTipo = Notificacion.nueva(usuario, evento, TipoNotificacion.ORDEN_CANCELADA, "t", "m", null,
                AHORA, "x");
        Notificacion otroUsuario = Notificacion.nueva(UUID.randomUUID(), evento, TipoNotificacion.ORDEN_EJECUTADA,
                "t", "m", null, AHORA, "x");

        assertThat(a.id()).isEqualTo(b.id()).isNotEqualTo(otroTipo.id()).isNotEqualTo(otroUsuario.id());
        assertThat(a.leida()).isFalse();
    }

    @Test
    void marcarLeidaEsIdempotenteYConservaLaPrimeraFecha() {
        Notificacion n = Notificacion.nueva(UUID.randomUUID(), UUID.randomUUID(), TipoNotificacion.ALERTA_PRECIO,
                "t", "m", null, AHORA, "x");
        Notificacion leida = n.marcarLeida(AHORA.plusSeconds(10));
        assertThat(leida.leida()).isTrue();
        assertThat(leida.marcarLeida(AHORA.plusSeconds(99)).leidaEn()).isEqualTo(AHORA.plusSeconds(10));
    }

    @Test
    void recortaTextosLargos() {
        Notificacion n = Notificacion.nueva(UUID.randomUUID(), UUID.randomUUID(), TipoNotificacion.SUSCRIPCION,
                "x".repeat(300), "y".repeat(900), null, AHORA, "x");
        assertThat(n.titulo()).hasSize(Notificacion.MAX_TITULO).endsWith("…");
        assertThat(n.mensaje()).hasSize(Notificacion.MAX_MENSAJE);
    }

    @Test
    void elEnvioEsperaExponencialmenteYQuedaFallidoAlAgotarIntentos() {
        Duration base = Duration.ofSeconds(30);
        EnvioNotificacion envio = EnvioNotificacion.pendiente(UUID.randomUUID(), CanalNotificacion.SMS, AHORA);

        EnvioNotificacion primero = envio.fallido("caído", AHORA, 3, base);
        assertThat(primero.estado()).isEqualTo(EnvioNotificacion.Estado.PENDIENTE);
        assertThat(primero.proximoIntento()).isEqualTo(AHORA.plusSeconds(30));

        EnvioNotificacion segundo = primero.fallido("caído", AHORA, 3, base);
        assertThat(segundo.proximoIntento()).isEqualTo(AHORA.plusSeconds(60));

        EnvioNotificacion tercero = segundo.fallido("caído", AHORA, 3, base);
        assertThat(tercero.estado()).isEqualTo(EnvioNotificacion.Estado.FALLIDO);
        assertThat(tercero.intentos()).isEqualTo(3);
        assertThat(tercero.proximoIntento()).isNull();
    }

    @Test
    void soloLaEjecucionCompletaNotifica() {
        UUID cuenta = UUID.randomUUID();
        UUID orden = UUID.randomUUID();
        assertThat(traductor.traducir(new Eventos.OrdenEjecutada(META, orden, cuenta, "AAPL", 5,
                new BigDecimal("190.5"), false))).isEmpty();

        List<Aviso> avisos = traductor.traducir(new Eventos.OrdenEjecutada(META, orden, cuenta, "AAPL", 10,
                new BigDecimal("190.5"), true));
        assertThat(avisos).singleElement().satisfies(a -> {
            assertThat(a.tipo()).isEqualTo(TipoNotificacion.ORDEN_EJECUTADA);
            assertThat(a.destinatario()).isEqualTo(new Aviso.TitularDeCuenta(cuenta));
            assertThat(a.mensaje()).contains("AAPL").contains("$190.50");
            assertThat(a.referenciaId()).isEqualTo(orden.toString());
        });
    }

    @Test
    void laCancelacionNotificaYOtrosCambiosDeEstadoNo() {
        UUID cuenta = UUID.randomUUID();
        assertThat(traductor.traducir(new Eventos.OrdenEstadoCambiado(META, UUID.randomUUID(), cuenta,
                "PENDIENTE", "EN_EJECUCION", null))).isEmpty();
        assertThat(traductor.traducir(new Eventos.OrdenEstadoCambiado(META, UUID.randomUUID(), cuenta,
                "PENDIENTE", "CANCELADA", "Fondos insuficientes")))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.tipo()).isEqualTo(TipoNotificacion.ORDEN_CANCELADA);
                    assertThat(a.mensaje()).contains("Fondos insuficientes");
                });
    }

    @Test
    void laAsociacionNotificaAAmbasPartes() {
        UUID inversionista = UUID.randomUUID();
        UUID comisionista = UUID.randomUUID();
        assertThat(traductor.traducir(new Eventos.AsociacionComisionistaCambiada(META, inversionista, comisionista,
                "ASIGNADA")))
                .extracting(Aviso::destinatario)
                .containsExactly(new Aviso.Usuario(inversionista), new Aviso.Usuario(comisionista));
    }

    @Test
    void soloLosTiposDelCatalogoTienenPreferencia() {
        assertThat(TipoNotificacion.ORDEN_EJECUTADA.eventoPreferencia()).contains("ORDEN_EJECUTADA");
        assertThat(TipoNotificacion.ORDEN_CANCELADA.eventoPreferencia()).isEmpty();
    }

    @Test
    void validaElEstadoDeLectura() {
        assertThatThrownBy(() -> new Notificacion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                TipoNotificacion.SUSCRIPCION, "t", "m", null, true, AHORA, null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
