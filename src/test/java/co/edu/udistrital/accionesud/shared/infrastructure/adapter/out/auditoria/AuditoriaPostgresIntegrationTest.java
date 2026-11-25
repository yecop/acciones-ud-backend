package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.auditoria;

import co.edu.udistrital.accionesud.shared.application.port.out.PublicadorEventosPort;
import co.edu.udistrital.accionesud.shared.application.port.out.RegistroAuditoriaPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.application.service.RegistradorAuditoria;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.trace.TraceIdFilter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionSpringAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import co.edu.udistrital.accionesud.support.PostgresSpringSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-33 contra PostgreSQL real: transacción de Spring, auditoría en {@code auditoria_evento}
 * con traceId, eventos solo tras el commit y tabla de auditoría inmutable.
 *
 * <p>Usa el contenedor compartido de {@link PostgresSpringSupport} (SCRUM-34): se omite sin
 * Docker en local y el guard del CI exige que se ejecute. Cada prueba usa un traceId único, así
 * que no necesita limpiar {@code auditoria_evento} (que además es inmutable).</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class AuditoriaPostgresIntegrationTest extends PostgresSpringSupport {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("accionesud.auth.reintento.habilitado", () -> "false");
        registry.add("accionesud.mercados.cotizaciones.actualizador-habilitado", () -> "false");
        registry.add("accionesud.mercados.cotizaciones.cache.tipo", () -> "memoria");
        registry.add("management.health.redis.enabled", () -> "false");
    }

    static class Consumidor {
        final List<Eventos.MovimientoFondosRegistrado> recibidos = new CopyOnWriteArrayList<>();

        @EventListener
        void alRegistrar(Eventos.MovimientoFondosRegistrado evento) {
            recibidos.add(evento);
        }
    }

    @TestConfiguration
    static class Configuracion {
        @Bean
        Consumidor consumidorPostgres() {
            return new Consumidor();
        }
    }

    @Autowired TransaccionPort transaccion;
    @Autowired RegistradorAuditoria auditoria;
    @Autowired RegistroAuditoriaPort puertoAuditoria;
    @Autowired PublicadorEventosPort eventos;
    @Autowired Consumidor consumidor;

    private String trace;

    @BeforeEach
    void preparar() {
        trace = UUID.randomUUID().toString();
        MDC.put(TraceIdFilter.MDC_KEY, trace);
        consumidor.recibidos.clear();
    }

    @AfterEach
    void limpiarMdc() {
        MDC.remove(TraceIdFilter.MDC_KEY);
    }

    private int auditadosConEsteTrace() {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM auditoria_evento WHERE trace_id = ?",
                Integer.class, trace);
        return total == null ? 0 : total;
    }

    private void depositar(UUID cuenta) {
        auditoria.exitoso(null, "DEPOSITO", "CUENTA", cuenta.toString(), Map.of("monto", "50.00"));
        eventos.publicar(new Eventos.MovimientoFondosRegistrado(auditoria.metadatosEvento(null), cuenta,
                "DEPOSITO", new BigDecimal("50.00"), new BigDecimal("50.00")));
    }

    @Test
    void conBaseDeDatosUsaTransaccionDeSpringYAuditoriaJdbc() {
        assertThat(transaccion).isInstanceOf(TransaccionSpringAdapter.class);
        assertThat(puertoAuditoria).isInstanceOf(AuditoriaJdbcAdapter.class);
    }

    @Test
    void elCommitPersisteLaAuditoriaConTraceIdYLuegoEntregaElEvento() {
        UUID cuenta = UUID.randomUUID();
        transaccion.enTransaccion(() -> {
            depositar(cuenta);
            assertThat(consumidor.recibidos).as("antes del commit").isEmpty();
        });

        assertThat(auditadosConEsteTrace()).isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT accion, entidad, entidad_id, resultado, detalle::text AS detalle FROM auditoria_evento "
                        + "WHERE trace_id = ?", trace);
        assertThat(fila).containsEntry("accion", "DEPOSITO").containsEntry("entidad", "CUENTA")
                .containsEntry("entidad_id", cuenta.toString()).containsEntry("resultado", "EXITOSO");
        assertThat((String) fila.get("detalle")).contains("\"monto\"").contains("50.00");
        assertThat(consumidor.recibidos).singleElement()
                .satisfies(e -> assertThat(e.metadatos().traceId()).isEqualTo(trace));
    }

    @Test
    void elRollbackNoDejaAuditoriaNiEventos() {
        assertThatThrownBy(() -> transaccion.enTransaccion(() -> {
            depositar(UUID.randomUUID());
            throw new IllegalStateException("falla después de escribir");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(auditadosConEsteTrace()).isZero();
        assertThat(consumidor.recibidos).isEmpty();
    }

    @Test
    void laAuditoriaNoSePuedeEditarNiBorrar() {
        transaccion.enTransaccion(() -> depositar(UUID.randomUUID()));

        assertThatThrownBy(() -> jdbc.update("UPDATE auditoria_evento SET accion = 'ALTERADA' WHERE trace_id = ?", trace))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria_evento WHERE trace_id = ?", trace))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE auditoria_evento"))
                .isInstanceOf(DataAccessException.class);
        assertThat(auditadosConEsteTrace()).isEqualTo(1);
    }
}
