package co.edu.udistrital.accionesud.notificaciones.infrastructure;

import co.edu.udistrital.accionesud.notificaciones.application.port.in.ReintentarEnviosUseCase;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Contacto;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.canal.CanalSimuladoAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia.NotificacionJdbcAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.config.NotificacionesBeansConfig.AlmacenNotificaciones;
import co.edu.udistrital.accionesud.shared.application.port.out.PublicadorEventosPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.domain.evento.MetadatosEvento;
import co.edu.udistrital.accionesud.support.PostgresSpringSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-51 contra PostgreSQL real (V13): el evento publicado dentro de una transacción crea la
 * notificación tras el commit, respeta las preferencias de SCRUM-22, no se duplica y un fallo del
 * proveedor se reintenta sobre la misma fila. Está en el guard del CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NotificacionesPostgresIntegrationTest extends PostgresSpringSupport {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("accionesud.auth.reintento.habilitado", () -> "false");
        registry.add("accionesud.notificaciones.reintento.habilitado", () -> "false");
        registry.add("accionesud.notificaciones.canales.email", () -> "simulado");
        registry.add("accionesud.mercados.cotizaciones.actualizador-habilitado", () -> "false");
        registry.add("accionesud.mercados.cotizaciones.cache.tipo", () -> "memoria");
        registry.add("management.health.redis.enabled", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired TransaccionPort transaccion;
    @Autowired PublicadorEventosPort eventos;
    @Autowired ReintentarEnviosUseCase reintentos;
    @Autowired AlmacenNotificaciones almacen;
    @Autowired @Qualifier("canalEmailSimulado") CanalSimuladoAdapter email;
    @Autowired @Qualifier("canalSmsSimulado") CanalSimuladoAdapter sms;

    private UUID usuario;
    private UUID cuenta;

    @BeforeEach
    void preparar() {
        email.reiniciar();
        sms.reiniciar();
        usuario = UUID.randomUUID();
        cuenta = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, rol_id, estado_usuario_id, nombre, correo, contrasena_hash)
                VALUES (?, 1, 1, 'Ana', ?, 'hash')""", usuario, "ana-" + usuario + "@example.test");
        jdbc.update("INSERT INTO inversionista (usuario_id, nivel_experiencia_id) VALUES (?, 1)", usuario);
        jdbc.update("INSERT INTO cuenta_corretaje (id, inversionista_id) VALUES (?, ?)", cuenta, usuario);
        // Preferencias de SCRUM-22: solo correo y solo para órdenes ejecutadas.
        jdbc.update("INSERT INTO preferencia_canal (inversionista_id, canal_id) VALUES (?, 2)", usuario);
        jdbc.update("""
                INSERT INTO preferencia_evento (inversionista_id, evento_id)
                SELECT ?, id FROM evento_notificacion WHERE nombre = 'ORDEN_EJECUTADA'""", usuario);
    }

    private Eventos.OrdenEjecutada ejecutada() {
        return new Eventos.OrdenEjecutada(MetadatosEvento.nuevo(Instant.now(), "trace-" + usuario, usuario),
                UUID.randomUUID(), cuenta, "NVDA", 4, new BigDecimal("135.20"), true);
    }

    private int notificaciones() {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE usuario_id = ?",
                Integer.class, usuario);
        return total == null ? 0 : total;
    }

    private Map<String, Object> envio(int canal) {
        return jdbc.queryForMap("""
                SELECT e.estado, e.intentos, e.ultimo_error FROM notificacion_envio e
                JOIN notificacion n ON n.id = e.notificacion_id
                WHERE n.usuario_id = ? AND e.canal_id = ?""", usuario, canal);
    }

    @Test
    void usaLosAdaptadoresJdbc() {
        assertThat(almacen.notificaciones()).isInstanceOf(NotificacionJdbcAdapter.class);
        assertThat(almacen.destinatarios().contacto(usuario))
                .contains(new Contacto("ana-" + usuario + "@example.test", null));
        assertThat(almacen.destinatarios().titularDeCuenta(cuenta)).contains(usuario);
    }

    @Test
    void trasElCommitPersisteLaNotificacionYLaEnviaSoloPorLosCanalesElegidos() {
        Eventos.OrdenEjecutada evento = ejecutada();
        transaccion.enTransaccion(() -> eventos.publicar(evento));

        assertThat(notificaciones()).isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT tipo, leida, trace_id, evento_id FROM notificacion WHERE usuario_id = ?", usuario);
        assertThat(fila).containsEntry("tipo", "ORDEN_EJECUTADA").containsEntry("leida", false)
                .containsEntry("trace_id", "trace-" + usuario).containsEntry("evento_id", evento.metadatos().id());
        assertThat(envio(2)).containsEntry("estado", "ENVIADO").containsEntry("intentos", 1);
        assertThat(email.enviadas()).hasSize(1);
        assertThat(sms.intentos()).isZero();

        transaccion.enTransaccion(() -> eventos.publicar(evento));
        assertThat(notificaciones()).as("evento reprocesado").isEqualTo(1);
        assertThat(email.intentos()).isEqualTo(1);
    }

    @Test
    void siLaTransaccionHaceRollbackNoHayNotificacion() {
        assertThatThrownBy(() -> transaccion.enTransaccion(() -> {
            eventos.publicar(ejecutada());
            throw new IllegalStateException("falla la orden");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(notificaciones()).isZero();
        assertThat(email.intentos()).isZero();
    }

    @Test
    void unFalloDelProveedorSeReintentaSobreLaMismaFila() {
        email.fallarProximos(1);
        transaccion.enTransaccion(() -> eventos.publicar(ejecutada()));

        assertThat(envio(2)).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1);
        assertThat((String) envio(2).get("ultimo_error")).contains("no disponible");

        // Vence la espera exponencial.
        jdbc.update("""
                UPDATE notificacion_envio SET proximo_intento = ?
                WHERE notificacion_id IN (SELECT id FROM notificacion WHERE usuario_id = ?)""",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.SECONDS)), usuario);
        assertThat(reintentos.reintentarPendientes()).isGreaterThanOrEqualTo(1);

        assertThat(envio(2)).containsEntry("estado", "ENVIADO").containsEntry("intentos", 2);
        assertThat(email.enviadas()).hasSize(1);
        assertThat(notificaciones()).isEqualTo(1);
    }

    @Test
    void laBandejaRestFiltraYSoloDejaMarcarLasPropias() throws Exception {
        transaccion.enTransaccion(() -> eventos.publicar(ejecutada()));
        transaccion.enTransaccion(() -> eventos.publicar(new Eventos.OrdenEstadoCambiado(
                MetadatosEvento.nuevo(Instant.now(), "trace-c", usuario), UUID.randomUUID(), cuenta,
                "PENDIENTE", "CANCELADA", "Mercado cerrado")));

        String cuerpo = mvc.perform(get("/api/v1/notificaciones").param("tipo", "ORDEN_CANCELADA")
                        .with(user(usuario.toString()).roles("INVERSIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].mensaje").value("Tu orden fue cancelada. Motivo: Mercado cerrado."))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(cuerpo, "$.contenido[0].id");

        mvc.perform(patch("/api/v1/notificaciones/" + id + "/leida")
                        .with(user(UUID.randomUUID().toString()).roles("INVERSIONISTA")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/notificaciones/" + id + "/leida")
                        .with(user(usuario.toString()).roles("INVERSIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));
        mvc.perform(get("/api/v1/notificaciones").param("leida", "false")
                        .with(user(usuario.toString()).roles("INVERSIONISTA")))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(patch("/api/v1/notificaciones/leidas").with(user(usuario.toString()).roles("INVERSIONISTA")))
                .andExpect(jsonPath("$.marcadas").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND leida_en IS NULL",
                Integer.class, usuario)).isZero();
    }
}
