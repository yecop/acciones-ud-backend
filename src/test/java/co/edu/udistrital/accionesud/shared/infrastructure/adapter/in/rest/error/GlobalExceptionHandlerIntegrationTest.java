package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error;

import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AuthProperties authProperties;

    @Test
    void responde400ConErroresDeValidacion() throws Exception {
        String traceId = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(post("/api/v1/test/validacion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVERSIONISTA")))
                        .header("X-Trace-Id", traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode cuerpo = verificarContrato(result, 400, "ERROR_VALIDACION",
                "/api/v1/test/validacion");
        assertThat(cuerpo.get("traceId").asText()).isEqualTo(traceId);
        assertThat(cuerpo.get("detalles").size()).isGreaterThan(0);
        assertThat(cuerpo.at("/datos/campos").isArray()).isTrue();
    }

    @Test
    void responde401SinCredenciales() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test/trace"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andReturn();

        verificarContrato(result, 401, "NO_AUTENTICADO", "/api/v1/test/trace");
    }

    @Test
    void responde401ConTokenExpirado() throws Exception {
        Instant expiracion = Instant.now().minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getEmisor())
                .subject(UUID.randomUUID().toString())
                .issuedAt(expiracion.minusSeconds(900))
                .expiresAt(expiracion)
                .claim("rol", "INVERSIONISTA")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        MvcResult result = mockMvc.perform(get("/api/v1/test/trace")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andReturn();

        verificarContrato(result, 401, "TOKEN_EXPIRADO", "/api/v1/test/trace");
    }

    @Test
    void responde403CuandoElRolNoAlcanza() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/ping")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVERSIONISTA"))))
                .andExpect(status().isForbidden())
                .andReturn();

        verificarContrato(result, 403, "ACCESO_DENEGADO", "/api/v1/admin/ping");
    }

    @Test
    void responde404CuandoLaRutaNoExiste() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/no-existe")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVERSIONISTA"))))
                .andExpect(status().isNotFound())
                .andReturn();

        verificarContrato(result, 404, "RECURSO_NO_ENCONTRADO", "/api/v1/no-existe");
    }

    @Test
    void responde500SinExponerDetalleInterno() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test/error")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVERSIONISTA"))))
                .andExpect(status().isInternalServerError())
                .andReturn();

        JsonNode cuerpo = verificarContrato(result, 500, "ERROR_INTERNO", "/api/v1/test/error");
        assertThat(cuerpo.get("mensaje").asText()).doesNotContain("fallo simulado");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("fallo simulado");
    }

    private JsonNode verificarContrato(MvcResult result, int status, String codigo,
                                       String ruta) throws Exception {
        String traceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(traceId).isNotBlank();

        String contenido = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode cuerpo = objectMapper.readTree(contenido);

        assertThat(cuerpo.get("timestamp").asText()).isNotBlank();
        assertThatCode(() -> Instant.parse(cuerpo.get("timestamp").asText()))
                .doesNotThrowAnyException();
        assertThat(cuerpo.get("status").asInt()).isEqualTo(status);
        assertThat(cuerpo.get("codigo").asText()).isEqualTo(codigo);
        assertThat(cuerpo.get("mensaje").asText()).isNotBlank();
        assertThat(cuerpo.get("traceId").asText()).isEqualTo(traceId);
        assertThat(cuerpo.get("ruta").asText()).isEqualTo(ruta);
        assertThat(cuerpo.get("detalles").isArray()).isTrue();
        assertThat(cuerpo.get("datos").isObject()).isTrue();

        return cuerpo;
    }
}
