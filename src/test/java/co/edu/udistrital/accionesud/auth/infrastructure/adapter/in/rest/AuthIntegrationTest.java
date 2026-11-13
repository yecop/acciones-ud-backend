package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.auth.application.port.out.NotificadorMfaPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {

    private static final String CORREO = "inversionista@accionesud.local";
    private static final String CLAVE = "Clave123!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("accionesud.auth.jwt.secreto",
                () -> "integration-secreto-jwt-acciones-ud-2026-32bytes");
        registry.add("accionesud.auth.mfa.reenvio-espera-segundos", () -> "0");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificadorMfaPort notificador;

    @Test
    void flujoCompletoLoginMfaRefreshYLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.canal").value("EMAIL"))
                .andExpect(jsonPath("$.expiraEn").value(300))
                .andReturn();

        String mfaToken = json(login).get("mfaToken").asText();
        assertThat(mfaToken).isNotBlank();
        String primerCodigo = ultimoCodigoEnviado();

        mockMvc.perform(post("/api/v1/auth/mfa/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + mfaToken + "\"}"))
                .andExpect(status().isNoContent());

        String segundoCodigo = ultimoCodigoEnviado();
        assertThat(segundoCodigo).isNotEqualTo(primerCodigo);

        mockMvc.perform(post("/api/v1/auth/mfa/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + mfaToken + "\",\"codigo\":\""
                                + primerCodigo + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("MFA_INVALIDO"));

        MvcResult verificacion = mockMvc.perform(post("/api/v1/auth/mfa/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + mfaToken + "\",\"codigo\":\""
                                + segundoCodigo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value(900))
                .andExpect(jsonPath("$.usuario.correo").doesNotExist())
                .andExpect(jsonPath("$.usuario.rol").value("INVERSIONISTA"))
                .andReturn();

        JsonNode tokens = json(verificacion);
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        mockMvc.perform(get("/api/v1/test/trace"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        org.hamcrest.Matchers.startsWith("Bearer")));

        mockMvc.perform(get("/api/v1/test/trace")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        MvcResult refresco = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokensRotados = json(refresco);
        String refreshRotado = tokensRotados.get("refreshToken").asText();
        assertThat(refreshRotado).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshRotado + "\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult segundoLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String segundoMfaToken = json(segundoLogin).get("mfaToken").asText();
        String tercerCodigo = ultimoCodigoEnviado();

        MvcResult segundoVerificacion = mockMvc.perform(post("/api/v1/auth/mfa/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + segundoMfaToken + "\",\"codigo\":\""
                                + tercerCodigo + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode segundoTokens = json(segundoVerificacion);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization",
                                "Bearer " + segundoTokens.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\""
                                + segundoTokens.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\""
                                + segundoTokens.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String ultimoCodigoEnviado() {
        ArgumentCaptor<String> codigos = ArgumentCaptor.forClass(String.class);
        verify(notificador, atLeastOnce()).enviarCodigo(eq(CORREO), codigos.capture(), any());
        return codigos.getAllValues().get(codigos.getAllValues().size() - 1);
    }
}
