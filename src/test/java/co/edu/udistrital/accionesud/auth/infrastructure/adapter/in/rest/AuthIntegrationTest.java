package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.auth.application.port.out.HasheadorPort;
import co.edu.udistrital.accionesud.auth.application.port.out.NotificadorMfaPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
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

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private NotificadorMfaPort notificador;

    @SpyBean
    private HasheadorPort hasheador;

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

    @Test
    @Timeout(30)
    void verificarMfaConcurrenteMismoTokenSoloUnoEmiteTokens() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String mfaToken = json(login).get("mfaToken").asText();
        String codigo = ultimoCodigoEnviado();

        UUID usuarioId = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE lower(correo) = lower(?)", UUID.class, CORREO);
        Integer refrescosAntes = jdbc.queryForObject(
                "SELECT count(*) FROM auth_token_refresco WHERE usuario_id = ?",
                Integer.class, usuarioId);
        Integer exitososAntes = jdbc.queryForObject(
                "SELECT count(*) FROM auth_intento WHERE tipo = 'MFA_VERIFICACION' "
                        + "AND resultado = 'EXITOSO'", Integer.class);
        Integer fallidosAntes = jdbc.queryForObject(
                "SELECT count(*) FROM auth_intento WHERE tipo = 'MFA_VERIFICACION' "
                        + "AND resultado = 'FALLIDO' "
                        + "AND motivo = 'desafío MFA consumido en paralelo'", Integer.class);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        CyclicBarrier barrera = new CyclicBarrier(2);
        doAnswer(invocation -> {
            boolean coincide = Boolean.TRUE.equals(invocation.callRealMethod());
            barrera.await(10, TimeUnit.SECONDS);
            return coincide;
        }).when(hasheador).coincide(anyString(), anyString());

        Callable<Respuesta> peticion = () -> {
            salida.await(10, TimeUnit.SECONDS);
            MvcResult resultado = mockMvc.perform(post("/api/v1/auth/mfa/verificar")
                            .header("X-Trace-Id", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mfaToken\":\"" + mfaToken + "\",\"codigo\":\""
                                    + codigo + "\"}"))
                    .andReturn();
            return new Respuesta(resultado.getResponse().getStatus(),
                    resultado.getResponse().getContentAsString());
        };

        try {
            Future<Respuesta> primera = executor.submit(peticion);
            Future<Respuesta> segunda = executor.submit(peticion);
            salida.countDown();

            List<Respuesta> respuestas = List.of(
                    primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));

            assertThat(respuestas).extracting(Respuesta::status)
                    .containsExactlyInAnyOrder(200, 401);
            Respuesta exitosa = respuestas.stream()
                    .filter(respuesta -> respuesta.status() == 200).findFirst().orElseThrow();
            Respuesta rechazada = respuestas.stream()
                    .filter(respuesta -> respuesta.status() == 401).findFirst().orElseThrow();

            JsonNode tokens = objectMapper.readTree(exitosa.cuerpo());
            assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
            assertThat(tokens.path("expiraEn").asInt()).isEqualTo(900);
            String accessToken = tokens.path("accessToken").asText();
            String refreshToken = tokens.path("refreshToken").asText();
            assertThat(accessToken).isNotBlank();
            assertThat(refreshToken).isNotBlank();

            JsonNode error = objectMapper.readTree(rechazada.cuerpo());
            assertThat(error.path("codigo").asText()).isEqualTo("MFA_INVALIDO");
            assertThat(rechazada.cuerpo()).doesNotContain(
                    mfaToken, codigo, accessToken, refreshToken);

            Integer consumidos = jdbc.queryForObject(
                    "SELECT count(*) FROM auth_mfa_desafio WHERE token_hash = ? "
                            + "AND usado_en IS NOT NULL", Integer.class, hasheador.hash(mfaToken));
            Integer refrescosDespues = jdbc.queryForObject(
                    "SELECT count(*) FROM auth_token_refresco WHERE usuario_id = ?",
                    Integer.class, usuarioId);
            Integer exitososDespues = jdbc.queryForObject(
                    "SELECT count(*) FROM auth_intento WHERE tipo = 'MFA_VERIFICACION' "
                            + "AND resultado = 'EXITOSO'", Integer.class);
            Integer fallidosDespues = jdbc.queryForObject(
                    "SELECT count(*) FROM auth_intento WHERE tipo = 'MFA_VERIFICACION' "
                            + "AND resultado = 'FALLIDO' "
                            + "AND motivo = 'desafío MFA consumido en paralelo'", Integer.class);

            assertThat(consumidos).isEqualTo(1);
            assertThat(refrescosDespues - refrescosAntes).isEqualTo(1);
            assertThat(exitososDespues - exitososAntes).isEqualTo(1);
            assertThat(fallidosDespues - fallidosAntes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            Mockito.doCallRealMethod().when(hasheador).coincide(anyString(), anyString());
        }
    }

    @Test
    @Timeout(60)
    void verificarMfaConcurrenteCodigosIncorrectosNoSuperaElMaximoDeIntentos() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String mfaToken = json(login).get("mfaToken").asText();
        String codigo = ultimoCodigoEnviado();
        String codigoIncorrecto = "000000".equals(codigo) ? "999999" : "000000";
        String tokenHash = hasheador.hash(mfaToken);

        Integer intentosAntes = jdbc.queryForObject(
                "SELECT intentos FROM auth_mfa_desafio WHERE token_hash = ?",
                Integer.class, tokenHash);
        Integer fallidosAntes = contarIntentos("FALLIDO", "código MFA incorrecto");
        Integer maximosAntes = contarIntentos("BLOQUEADO", "máximo de intentos MFA");
        Integer bloqueadosAntes = contarIntentos("BLOQUEADO", "desafío MFA bloqueado");

        int concurrentes = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentes);
        CountDownLatch salida = new CountDownLatch(1);
        CyclicBarrier barrera = new CyclicBarrier(concurrentes);
        doAnswer(invocation -> {
            boolean coincide = Boolean.TRUE.equals(invocation.callRealMethod());
            barrera.await(30, TimeUnit.SECONDS);
            return coincide;
        }).when(hasheador).coincide(anyString(), anyString());

        Callable<Respuesta> peticion = () -> {
            salida.await(30, TimeUnit.SECONDS);
            MvcResult resultado = mockMvc.perform(post("/api/v1/auth/mfa/verificar")
                            .header("X-Trace-Id", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mfaToken\":\"" + mfaToken + "\",\"codigo\":\""
                                    + codigoIncorrecto + "\"}"))
                    .andReturn();
            return new Respuesta(resultado.getResponse().getStatus(),
                    resultado.getResponse().getContentAsString());
        };

        try {
            List<Future<Respuesta>> futuros = new ArrayList<>();
            for (int i = 0; i < concurrentes; i++) {
                futuros.add(executor.submit(peticion));
            }
            salida.countDown();

            List<Respuesta> respuestas = new ArrayList<>();
            for (Future<Respuesta> futuro : futuros) {
                respuestas.add(futuro.get(30, TimeUnit.SECONDS));
            }

            assertThat(respuestas).extracting(Respuesta::status)
                    .containsExactlyInAnyOrder(401, 401, 401, 401, 401, 429, 429, 429);
            assertThat(respuestas).filteredOn(respuesta -> respuesta.status() == 401)
                    .allSatisfy(respuesta -> assertThat(respuesta.cuerpo())
                            .contains("\"codigo\":\"MFA_INVALIDO\""));
            assertThat(respuestas).filteredOn(respuesta -> respuesta.status() == 429)
                    .allSatisfy(respuesta -> assertThat(respuesta.cuerpo())
                            .contains("\"codigo\":\"DEMASIADAS_SOLICITUDES\""));

            Integer intentosDespues = jdbc.queryForObject(
                    "SELECT intentos FROM auth_mfa_desafio WHERE token_hash = ?",
                    Integer.class, tokenHash);
            Boolean bloqueado = jdbc.queryForObject(
                    "SELECT bloqueado_hasta IS NOT NULL FROM auth_mfa_desafio WHERE token_hash = ?",
                    Boolean.class, tokenHash);

            assertThat(intentosDespues).isEqualTo(intentosAntes + 5);
            assertThat(bloqueado).isTrue();
            assertThat(contarIntentos("FALLIDO", "código MFA incorrecto") - fallidosAntes)
                    .isEqualTo(4);
            assertThat(contarIntentos("BLOQUEADO", "máximo de intentos MFA") - maximosAntes)
                    .isEqualTo(1);
            assertThat(contarIntentos("BLOQUEADO", "desafío MFA bloqueado") - bloqueadosAntes)
                    .isEqualTo(3);
        } finally {
            executor.shutdownNow();
            Mockito.doCallRealMethod().when(hasheador).coincide(anyString(), anyString());
        }
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Integer contarIntentos(String resultado, String motivo) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM auth_intento WHERE tipo = 'MFA_VERIFICACION' "
                        + "AND resultado = ? AND motivo = ?",
                Integer.class, resultado, motivo);
    }

    private String ultimoCodigoEnviado() {
        ArgumentCaptor<String> codigos = ArgumentCaptor.forClass(String.class);
        verify(notificador, atLeastOnce()).enviarCodigo(eq(CORREO), codigos.capture(), any());
        return codigos.getAllValues().get(codigos.getAllValues().size() - 1);
    }

    private record Respuesta(int status, String cuerpo) {
    }
}
