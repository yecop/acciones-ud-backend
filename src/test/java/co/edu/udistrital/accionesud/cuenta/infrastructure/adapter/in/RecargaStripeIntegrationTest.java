package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.webhook.StripeFirmaVerificador;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Recarga con Stripe de punta a punta (SCRUM-46): la API de Stripe la simula WireMock y el webhook
 * se firma con el mismo secreto que usa la aplicación. Perfil test (almacenamiento en memoria).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecargaStripeIntegrationTest {

    private static final String SECRETO_WEBHOOK = "whsec_prueba_integracion";
    static final WireMockServer stripe = new WireMockServer(options().dynamicPort());

    static {
        stripe.start();
    }

    @AfterAll
    static void detener() {
        stripe.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("accionesud.integraciones.pagos", () -> "stripe");
        registry.add("accionesud.pagos.stripe.base-url", stripe::baseUrl);
        registry.add("accionesud.pagos.stripe.secret-key", () -> "sk_test_prueba");
        registry.add("accionesud.pagos.stripe.webhook-secret", () -> SECRETO_WEBHOOK);
    }

    @Autowired MockMvc mvc;
    @Autowired StripeFirmaVerificador verificador;
    @Autowired ObjectMapper json;

    private UUID ana;

    @BeforeEach
    void preparar() {
        stripe.resetAll();
        ana = UUID.randomUUID();
    }

    private static RequestPostProcessor inversionista(UUID id) {
        return user(id.toString()).roles("INVERSIONISTA");
    }

    /** Inicia una recarga y devuelve el id del movimiento. */
    private String iniciarRecarga(String clave, String monto) throws Exception {
        stripe.stubFor(WireMock.post(urlEqualTo("/v1/checkout/sessions")).willReturn(okJson("""
                {"id":"cs_test_abc","object":"checkout.session","status":"open",
                 "url":"https://checkout.stripe.com/c/pay/cs_test_abc","expires_at":1795800000}""")));
        String respuesta = mvc.perform(post("/api/v1/cuenta/recargas").with(inversionista(ana))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\": \"" + monto + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimiento.tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$.movimiento.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.urlPago").value("https://checkout.stripe.com/c/pay/cs_test_abc"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).path("movimiento").path("id").asText();
    }

    private String webhook(String eventoId, String tipo, String movimiento, long centavos) {
        return """
                {"id":"%s","object":"event","type":"%s","data":{"object":{"id":"cs_test_abc",
                "object":"checkout.session","payment_status":"paid","amount_total":%d,"currency":"usd",
                "client_reference_id":"%s","metadata":{"movimientoId":"%s"}}}}"""
                .formatted(eventoId, tipo, centavos, movimiento, movimiento);
    }

    private org.springframework.test.web.servlet.ResultActions enviarWebhook(String cuerpo, String firma)
            throws Exception {
        var solicitud = post("/api/v1/pagos/stripe/webhook")
                .contentType("application/json; charset=utf-8")
                .content(cuerpo.getBytes(StandardCharsets.UTF_8));
        if (firma != null) {
            solicitud = solicitud.header("Stripe-Signature", firma);
        }
        return mvc.perform(solicitud);
    }

    private String firmar(String cuerpo) {
        return verificador.encabezado(Instant.now().getEpochSecond(), cuerpo.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void creaLaSesionEnStripeConElMontoEnCentavosYLaClaveDeIdempotencia() throws Exception {
        String movimiento = iniciarRecarga("rec-1", "500.25");

        stripe.verify(postRequestedFor(urlEqualTo("/v1/checkout/sessions"))
                .withHeader("Authorization", equalTo("Bearer sk_test_prueba"))
                .withHeader("Idempotency-Key", equalTo("recarga-" + movimiento))
                .withRequestBody(containing("mode=payment"))
                .withRequestBody(containing("line_items%5B0%5D%5Bprice_data%5D%5Bunit_amount%5D=50025"))
                .withRequestBody(containing("line_items%5B0%5D%5Bprice_data%5D%5Bcurrency%5D=usd"))
                .withRequestBody(containing("metadata%5BmovimientoId%5D=" + movimiento))
                .withRequestBody(containing("client_reference_id=" + movimiento)));
        // El saldo no cambia hasta que Stripe confirme.
        mvc.perform(get("/api/v1/cuenta/saldo").with(inversionista(ana)))
                .andExpect(jsonPath("$.disponible").value("100000.00"));
    }

    @Test
    void elWebhookFirmadoAcreditaLaRecargaUnaSolaVez() throws Exception {
        String movimiento = iniciarRecarga("rec-1", "500.25");
        String cuerpo = webhook("evt_1", "checkout.session.completed", movimiento, 50025);

        enviarWebhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("APLICADO"));
        enviarWebhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("DUPLICADO"));

        mvc.perform(get("/api/v1/cuenta/saldo").with(inversionista(ana)))
                .andExpect(jsonPath("$.disponible").value("100500.25"));
        mvc.perform(get("/api/v1/cuenta/movimientos").param("tipo", "DEPOSITO").with(inversionista(ana)))
                .andExpect(jsonPath("$.contenido[?(@.id == '%s')].estado", movimiento).value("COMPLETADO"));
    }

    @Test
    void sinFirmaValidaNoSeAcreditaNada() throws Exception {
        String movimiento = iniciarRecarga("rec-1", "500.00");
        String cuerpo = webhook("evt_1", "checkout.session.completed", movimiento, 50000);

        enviarWebhook(cuerpo, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
        enviarWebhook(cuerpo, "t=" + Instant.now().getEpochSecond() + ",v1=" + "a".repeat(64))
                .andExpect(status().isBadRequest());
        String alterado = webhook("evt_1", "checkout.session.completed", movimiento, 9_999_999);
        enviarWebhook(alterado, firmar(cuerpo)).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/cuenta/saldo").with(inversionista(ana)))
                .andExpect(jsonPath("$.disponible").value("100000.00"));
    }

    @Test
    void unaSesionVencidaCancelaLaRecarga() throws Exception {
        String movimiento = iniciarRecarga("rec-1", "500.00");
        String cuerpo = webhook("evt_exp", "checkout.session.expired", movimiento, 50000);

        enviarWebhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("APLICADO"));

        mvc.perform(get("/api/v1/cuenta/movimientos").param("tipo", "DEPOSITO").with(inversionista(ana)))
                .andExpect(jsonPath("$.contenido[?(@.id == '%s')].estado", movimiento).value("CANCELADO"));
    }

    @Test
    void siStripeFallaRespondeBadGatewayYElDepositoQuedaFallido() throws Exception {
        stripe.stubFor(WireMock.post(urlEqualTo("/v1/checkout/sessions")).willReturn(WireMock.aResponse()
                .withStatus(500).withBody("{\"error\":{\"message\":\"Fallo interno\"}}")));

        mvc.perform(post("/api/v1/cuenta/recargas").with(inversionista(ana)).header("Idempotency-Key", "rec-x")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\": 20}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("PASARELA_NO_DISPONIBLE"));
        mvc.perform(get("/api/v1/cuenta/movimientos").param("tipo", "DEPOSITO").with(inversionista(ana)))
                .andExpect(jsonPath("$.contenido[?(@.monto == '20.00')].estado").value("FALLIDO"));
    }

    @Test
    void soloElTitularRecargaYElWebhookEsPublico() throws Exception {
        mvc.perform(post("/api/v1/cuenta/recargas").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\": 20}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/cuenta/recargas").with(user(UUID.randomUUID().toString()).roles("COMISIONISTA"))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content("{\"monto\": 20}"))
                .andExpect(status().isForbidden());
        // Sin JWT llega al controlador (no 401) y lo rechaza la firma.
        enviarWebhook("{}", null).andExpect(status().isBadRequest());
    }
}
