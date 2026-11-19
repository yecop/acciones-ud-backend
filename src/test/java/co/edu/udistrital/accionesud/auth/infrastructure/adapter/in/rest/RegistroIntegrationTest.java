package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RegistroIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final WireMockServer alpaca = new WireMockServer(options().dynamicPort());

    static {
        alpaca.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("accionesud.auth.alpaca.base-url", alpaca::baseUrl);
        registry.add("accionesud.auth.alpaca.api-key", () -> "key-test");
        registry.add("accionesud.auth.alpaca.api-secret", () -> "secret-test");
        registry.add("accionesud.auth.reintento.habilitado", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpiarStubs() {
        alpaca.resetAll();
    }

    private static String json(String correo) {
        return """
                {
                  "nombres": "Ana", "apellidos": "Pérez",
                  "correo": "%s", "contrasena": "Secreta123",
                  "celular": "+573001112233", "fechaNacimiento": "1999-05-20",
                  "nivelExperiencia": "PRINCIPIANTE", "nivelRiesgo": "MEDIO",
                  "objetivos": "Ahorrar para estudios",
                  "mercados": ["NASDAQ", "NYSE"], "sectores": ["Tecnología", "Finanzas"]
                }""".formatted(correo);
    }

    private static String correoUnico() {
        return "ana-" + UUID.randomUUID() + "@example.test";
    }

    private void registrar(String correo) throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON).content(json(correo)));
    }

    @Test
    void registroExitosoCreaUsuarioCuentaEIntereses() throws Exception {
        alpaca.stubFor(WireMock.post(urlEqualTo("/v1/accounts")).willReturn(okJson("{\"id\":\"acc-123\"}")));
        String correo = correoUnico();

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON).content(json(correo)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.usuarioId").exists());

        assertEquals("acc-123", jdbc.queryForObject("""
                SELECT c.alpaca_account_id FROM cuenta_corretaje c
                JOIN usuario u ON u.id = c.inversionista_id WHERE u.correo = ?""", String.class, correo));
        String hash = jdbc.queryForObject(
                "SELECT contrasena_hash FROM usuario WHERE correo = ?", String.class, correo);
        assertTrue(hash.startsWith("{bcrypt}"));
        assertFalse(hash.contains("Secreta123"));
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM inversionista_interes_mercado i
                JOIN usuario u ON u.id = i.inversionista_id WHERE u.correo = ?""", Integer.class, correo));
        alpaca.verify(1, postRequestedFor(urlEqualTo("/v1/accounts"))
                .withHeader("Authorization", matching("Basic .+")));
    }

    @Test
    void conAlpacaCaidoQuedaPendienteYElReintentoLoCompleta() throws Exception {
        alpaca.stubFor(WireMock.post(urlEqualTo("/v1/accounts")).willReturn(aResponse().withStatus(503)));
        String correo = correoUnico();

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON).content(json(correo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_ALTA"));
        assertNull(jdbc.queryForObject("""
                SELECT c.alpaca_account_id FROM cuenta_corretaje c
                JOIN usuario u ON u.id = c.inversionista_id WHERE u.correo = ?""", String.class, correo));

        alpaca.resetAll();
        alpaca.stubFor(WireMock.post(urlEqualTo("/v1/accounts")).willReturn(okJson("{\"id\":\"acc-456\"}")));

        mockMvc.perform(post("/api/v1/admin/altas/reintentar").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completadas", greaterThanOrEqualTo(1)));

        assertEquals("ACTIVO", jdbc.queryForObject("""
                SELECT e.nombre FROM usuario u JOIN estado_usuario e ON e.id = u.estado_usuario_id
                WHERE u.correo = ?""", String.class, correo));
        assertEquals("acc-456", jdbc.queryForObject("""
                SELECT c.alpaca_account_id FROM cuenta_corretaje c
                JOIN usuario u ON u.id = c.inversionista_id WHERE u.correo = ?""", String.class, correo));
    }

    @Test
    void reintentoRequiereRolAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/altas/reintentar").with(user("inv").roles("INVERSIONISTA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void correoDuplicadoDevuelve409() throws Exception {
        alpaca.stubFor(WireMock.post(urlEqualTo("/v1/accounts")).willReturn(okJson("{\"id\":\"acc-789\"}")));
        String correo = correoUnico();
        registrar(correo);

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON).content(json(correo.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void validacionesDevuelvenDetallePorCampo() throws Exception {
        String body = json("no-es-correo")
                .replace("Secreta123", "123")
                .replace("1999-05-20", LocalDate.now().minusYears(10).toString());

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.datos.campos[?(@.campo == 'correo')]").exists())
                .andExpect(jsonPath("$.datos.campos[?(@.campo == 'contrasena')]").exists())
                .andExpect(jsonPath("$.datos.campos[?(@.campo == 'mayorDeEdad')]").exists());
    }
}
