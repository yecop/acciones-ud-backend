package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración de los endpoints REST de mercados.
 *
 * <p>Se fija el {@link RelojPort} mediante un bean primario de prueba para obtener
 * resultados deterministas sin depender del reloj real.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MercadoControllerIntegrationTest {

    /** Lunes 2026-01-05, 11:00 en Nueva York → NYSE/NASDAQ abiertos. */
    private static final Instant INSTANTE_FIJO =
            ZonedDateTime.of(2026, 1, 5, 11, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();

    @TestConfiguration
    static class RelojFijoConfig {
        @Bean
        @Primary
        RelojPort relojFijo() {
            return () -> INSTANTE_FIJO;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listaMercadosSinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/mercados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.codigo == 'NYSE')].zonaHoraria").value("America/New_York"));
    }

    @Test
    void estadoNyseAbierto() throws Exception {
        mockMvc.perform(get("/api/v1/mercados/NYSE/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("NYSE"))
                .andExpect(jsonPath("$.abierto").value(true))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.proximoCierre").exists());
    }

    @Test
    void estadoTseCerrado() throws Exception {
        // A las 11:00 en NY es la 01:00 del día siguiente en Tokio → TSE cerrado.
        mockMvc.perform(get("/api/v1/mercados/TSE/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("TSE"))
                .andExpect(jsonPath("$.abierto").value(false))
                .andExpect(jsonPath("$.proximaApertura").exists());
    }

    @Test
    void mercadoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/mercados/XXXX/estado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("MERCADO_NO_ENCONTRADO"));
    }

    @Test
    void codigoEnMinusculasSeNormaliza() throws Exception {
        mockMvc.perform(get("/api/v1/mercados/nyse/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("NYSE"));
    }
}
