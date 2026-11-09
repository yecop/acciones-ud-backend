package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceIdIntegrationTest {

    private static final String USUARIO = "usuario-test";
    private static final String CLAVE = "clave-test";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generaTraceIdCuandoNoSeRecibeHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();

        String traceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(traceId).isNotBlank();
        assertThat(UUID.fromString(traceId)).isNotNull();
    }

    @Test
    void conservaTraceIdValidoRecibido() throws Exception {
        String traceId = UUID.randomUUID().toString();

        mockMvc.perform(get("/actuator/health").header(TraceIdFilter.HEADER, traceId))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.HEADER, traceId));
    }

    @Test
    void reemplazaTraceIdInvalido() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header(TraceIdFilter.HEADER, "no-es-un-uuid"))
                .andExpect(status().isOk())
                .andReturn();

        String traceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(traceId).isNotEqualTo("no-es-un-uuid");
        assertThat(UUID.fromString(traceId)).isNotNull();
    }

    @Test
    void propagaElTraceIdAlMdcYALaRespuesta() throws Exception {
        String traceId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/test/trace")
                        .with(httpBasic(USUARIO, CLAVE))
                        .header(TraceIdFilter.HEADER, traceId))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.HEADER, traceId))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    @Test
    void incluyeTraceIdEnRespuestasDeSeguridad() throws Exception {
        String traceId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/test/trace").header(TraceIdFilter.HEADER, traceId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.HEADER, traceId))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }
}
