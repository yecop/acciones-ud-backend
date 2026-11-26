package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia.NotificacionesEnMemoriaAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.config.NotificacionesBeansConfig.AlmacenNotificaciones;
import co.edu.udistrital.accionesud.shared.application.port.out.PublicadorEventosPort;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.domain.evento.MetadatosEvento;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de SCRUM-51 sobre el almacenamiento en memoria (perfil test, sin base de datos). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificacionesControllerIntegrationTest {

    private static final String URL = "/api/v1/notificaciones";

    @Autowired MockMvc mvc;
    @Autowired PublicadorEventosPort eventos;
    @Autowired AlmacenNotificaciones almacen;

    private UUID ana;
    private UUID cuentaAna;

    @BeforeEach
    void preparar() {
        ana = UUID.randomUUID();
        cuentaAna = UUID.randomUUID();
        ((NotificacionesEnMemoriaAdapter) almacen.destinatarios()).registrarCuenta(cuentaAna, ana);
    }

    private static RequestPostProcessor inversionista(UUID id) {
        return user(id.toString()).roles("INVERSIONISTA");
    }

    private void ejecutar(Instant cuando) {
        eventos.publicar(new Eventos.OrdenEjecutada(MetadatosEvento.nuevo(cuando, "trace-rest", ana),
                UUID.randomUUID(), cuentaAna, "AAPL", 2, new BigDecimal("190.10"), true));
    }

    private void cancelar(Instant cuando) {
        eventos.publicar(new Eventos.OrdenEstadoCambiado(MetadatosEvento.nuevo(cuando, "trace-rest", ana),
                UUID.randomUUID(), cuentaAna, "PENDIENTE", "CANCELADA", null));
    }

    @Test
    void listaLasNotificacionesDelUsuarioConElContratoDelFrontend() throws Exception {
        ejecutar(Instant.parse("2026-11-20T14:00:00Z"));
        cancelar(Instant.parse("2026-11-21T14:00:00Z"));

        mvc.perform(get(URL).with(inversionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.contenido[0].tipo").value("ORDEN_CANCELADA"))
                .andExpect(jsonPath("$.contenido[0].leida").value(false))
                .andExpect(jsonPath("$.contenido[0].creadaEn").value("2026-11-21T14:00:00Z"))
                .andExpect(jsonPath("$.contenido[1].tipo").value("ORDEN_EJECUTADA"))
                .andExpect(jsonPath("$.contenido[1].titulo").value("Orden ejecutada"))
                .andExpect(jsonPath("$.contenido[1].referenciaId").isNotEmpty());

        mvc.perform(get(URL).param("tipo", "ORDEN_EJECUTADA").with(inversionista(ana)))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get(URL).param("desde", "2026-11-21").with(inversionista(ana)))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].tipo").value("ORDEN_CANCELADA"));
        mvc.perform(get(URL).param("desde", "2026-11-20T15:00:00-05:00").with(inversionista(ana)))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get(URL).param("pagina", "1").param("tamano", "1").with(inversionista(ana)))
                .andExpect(jsonPath("$.contenido[0].tipo").value("ORDEN_EJECUTADA"))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void marcarLeidaEsIdempotenteYUnaAjenaRespondeNoEncontrado() throws Exception {
        eventos.publicar(new Eventos.MovimientoFondosRegistrado(
                MetadatosEvento.nuevo(Instant.parse("2026-11-22T10:00:00Z"), "trace-rest", ana), cuentaAna,
                "DEPOSITO", new BigDecimal("500"), new BigDecimal("1500")));
        String cuerpo = mvc.perform(get(URL).with(inversionista(ana))).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(cuerpo, "$.contenido[0].id");

        mvc.perform(patch(URL + "/" + id + "/leida").with(user(UUID.randomUUID().toString()).roles("COMISIONISTA")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(patch(URL + "/" + UUID.randomUUID() + "/leida").with(inversionista(ana)))
                .andExpect(status().isNotFound());

        mvc.perform(patch(URL + "/" + id + "/leida").with(inversionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.leida").value(true))
                .andExpect(jsonPath("$.titulo").value("Deposito registrado"))
                .andExpect(jsonPath("$.mensaje").value("Deposito por $500.00. Saldo disponible: $1500.00."))
                .andExpect(jsonPath("$.referenciaId").value(nullValue()));
        mvc.perform(patch(URL + "/" + id + "/leida").with(inversionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));

        mvc.perform(get(URL).param("leida", "false").with(inversionista(ana)))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void marcaTodasLasNoLeidasYDevuelveCuantas() throws Exception {
        ejecutar(Instant.parse("2026-11-23T10:00:00Z"));
        cancelar(Instant.parse("2026-11-23T11:00:00Z"));

        mvc.perform(patch(URL + "/leidas").with(inversionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(2));
        mvc.perform(patch(URL + "/leidas").with(inversionista(ana)))
                .andExpect(jsonPath("$.marcadas").value(0));
        mvc.perform(get(URL).param("leida", "true").with(inversionista(ana)))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void otroUsuarioNoVeLasNotificacionesAjenas() throws Exception {
        ejecutar(Instant.parse("2026-11-24T10:00:00Z"));
        mvc.perform(get(URL).with(inversionista(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void validaLosParametros() throws Exception {
        mvc.perform(get(URL).param("tamano", "0").param("pagina", "-1").param("desde", "ayer")
                        .with(inversionista(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.detalles.length()").value(3));
        mvc.perform(get(URL).param("tipo", "INVENTADO").with(inversionista(ana)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(URL + "/no-es-uuid/leida").with(inversionista(ana)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exigeAutenticacionYRolDeInversionistaOComisionista() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(user(UUID.randomUUID().toString()).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL).with(user(UUID.randomUUID().toString()).roles("COMISIONISTA")))
                .andExpect(status().isOk());
    }
}
