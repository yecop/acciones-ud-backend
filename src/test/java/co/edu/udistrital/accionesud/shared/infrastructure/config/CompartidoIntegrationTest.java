package co.edu.udistrital.accionesud.shared.infrastructure.config;

import co.edu.udistrital.accionesud.shared.application.port.out.AccesoComisionistaPort;
import co.edu.udistrital.accionesud.shared.application.port.out.AccesoPremiumPort;
import co.edu.udistrital.accionesud.shared.application.port.out.BrokerOrdenesPort;
import co.edu.udistrital.accionesud.shared.application.port.out.PublicadorEventosPort;
import co.edu.udistrital.accionesud.shared.application.port.out.RegistroAuditoriaPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.application.service.RegistradorAuditoria;
import co.edu.udistrital.accionesud.shared.domain.auditoria.RegistroAuditoria;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.auditoria.AuditoriaEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.broker.BrokerOrdenesSimuladoAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionLocalAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-33 sobre el contexto completo de Spring en el perfil {@code test} (sin base de datos):
 * wiring de los puertos, traceId de la solicitud en la auditoría, eventos a los
 * {@code @EventListener} solo tras el commit y errores de acceso como 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompartidoIntegrationTest {

    private static final String TRACE = "0f1e2d3c-4b5a-4968-8776-655443322110";

    /** Consumidor de eventos como lo haría notificaciones (SCRUM-51). */
    static class ConsumidorEventos {
        final List<Eventos.MovimientoFondosRegistrado> recibidos = new CopyOnWriteArrayList<>();

        @EventListener
        void alRegistrarMovimiento(Eventos.MovimientoFondosRegistrado evento) {
            recibidos.add(evento);
        }
    }

    /** Endpoints de prueba que usan los puertos como lo haría un caso de uso real. */
    @RestController
    @RequestMapping("/api/v1/test/compartido")
    static class EndpointsPrueba {
        private final TransaccionPort transaccion;
        private final RegistradorAuditoria auditoria;
        private final PublicadorEventosPort eventos;
        private final AccesoPremiumPort premium;
        private final AccesoComisionistaPort comisionistas;

        EndpointsPrueba(TransaccionPort transaccion, RegistradorAuditoria auditoria, PublicadorEventosPort eventos,
                        AccesoPremiumPort premium, AccesoComisionistaPort comisionistas) {
            this.transaccion = transaccion;
            this.auditoria = auditoria;
            this.eventos = eventos;
            this.premium = premium;
            this.comisionistas = comisionistas;
        }

        @PostMapping("/deposito")
        Map<String, String> deposito() {
            UUID cuenta = UUID.randomUUID();
            transaccion.enTransaccion(() -> {
                auditoria.exitoso(null, "DEPOSITO", "CUENTA", cuenta.toString(), Map.of("monto", "100.00"));
                eventos.publicar(new Eventos.MovimientoFondosRegistrado(auditoria.metadatosEvento(null), cuenta,
                        "DEPOSITO", new BigDecimal("100.00"), new BigDecimal("100.00")));
            });
            return Map.of("cuenta", cuenta.toString());
        }

        @PostMapping("/premium")
        void soloPremium() {
            premium.exigirPremium(UUID.randomUUID());
        }

        @PostMapping("/comisionista")
        void soloComisionistaAsociado() {
            comisionistas.exigirAcceso(UUID.randomUUID(), UUID.randomUUID());
        }
    }

    @TestConfiguration
    static class Configuracion {
        @Bean
        ConsumidorEventos consumidorEventos() {
            return new ConsumidorEventos();
        }

        @Bean
        EndpointsPrueba endpointsPrueba(TransaccionPort t, RegistradorAuditoria a, PublicadorEventosPort e,
                                        AccesoPremiumPort p, AccesoComisionistaPort c) {
            return new EndpointsPrueba(t, a, e, p, c);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ConsumidorEventos consumidor;
    @Autowired RegistroAuditoriaPort auditoria;
    @Autowired TransaccionPort transaccion;
    @Autowired BrokerOrdenesPort broker;

    @BeforeEach
    void limpiar() {
        consumidor.recibidos.clear();
        ((AuditoriaEnMemoriaAdapter) auditoria).limpiar();
    }

    @Test
    void sinBaseDeDatosUsaLosAdaptadoresLocalesYSimulados() {
        assertThat(transaccion).isInstanceOf(TransaccionLocalAdapter.class);
        assertThat(auditoria).isInstanceOf(AuditoriaEnMemoriaAdapter.class);
        assertThat(broker).isInstanceOf(BrokerOrdenesSimuladoAdapter.class);
    }

    @Test
    void laSolicitudAuditaConSuTraceIdYPublicaElEventoTrasElCommit() throws Exception {
        mockMvc.perform(post("/api/v1/test/compartido/deposito").header("X-Trace-Id", TRACE).with(user("inv")))
                .andExpect(status().isOk());

        List<RegistroAuditoria> registros = ((AuditoriaEnMemoriaAdapter) auditoria).registros();
        assertThat(registros).singleElement().satisfies(r -> {
            assertThat(r.traceId()).isEqualTo(TRACE);
            assertThat(r.accion()).isEqualTo("DEPOSITO");
        });
        assertThat(consumidor.recibidos).singleElement()
                .satisfies(e -> assertThat(e.metadatos().traceId()).isEqualTo(TRACE));
    }

    @Test
    void funcionPremiumSinSuscripcionResponde403() throws Exception {
        mockMvc.perform(post("/api/v1/test/compartido/premium").with(user("inv")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FUNCION_PREMIUM"));
    }

    @Test
    void comisionistaSinRelacionVigenteResponde403() throws Exception {
        mockMvc.perform(post("/api/v1/test/compartido/comisionista").with(user("com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }
}
