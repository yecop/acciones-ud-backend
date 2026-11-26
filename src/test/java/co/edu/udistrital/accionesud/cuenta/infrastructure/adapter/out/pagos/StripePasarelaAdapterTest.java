package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.pagos;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort.SesionPago;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort.SolicitudPago;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoDisponibleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Comportamiento ante los errores de la API de Stripe (docs.stripe.com/api/errors y error-handling). */
class StripePasarelaAdapterTest {

    private static final String RUTA = "/v1/checkout/sessions";
    private static final String SESION = """
            {"id":"cs_test_1","object":"checkout.session","status":"open",
             "url":"https://checkout.stripe.com/c/pay/cs_test_1","expires_at":1795800000}""";

    static final WireMockServer stripe = new WireMockServer(options().dynamicPort());

    private final List<Duration> esperas = new ArrayList<>();
    private StripePasarelaAdapter pasarela;
    private UUID movimiento;

    @BeforeAll
    static void iniciar() {
        stripe.start();
    }

    @AfterAll
    static void detener() {
        stripe.stop();
    }

    @BeforeEach
    void preparar() {
        stripe.resetAll();
        esperas.clear();
        movimiento = UUID.randomUUID();
        pasarela = new StripePasarelaAdapter(HttpClient.newHttpClient(), new ObjectMapper(), stripe.baseUrl(),
                "sk_test_prueba", "http://localhost:5173/fondos?recarga=exito", "http://localhost:5173/fondos",
                Duration.ofSeconds(5), new StripePasarelaAdapter.Reintentos(2, Duration.ofMillis(100)), esperas::add);
    }

    private SesionPago crear() {
        return pasarela.crearSesion(new SolicitudPago(movimiento, UUID.randomUUID(), new BigDecimal("12.34"), "USD"));
    }

    private static String error(String type, String code) {
        return "{\"error\":{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"message\":\"detalle\"}}";
    }

    private int intentos() {
        return stripe.findAll(postRequestedFor(urlEqualTo(RUTA))).size();
    }

    @Test
    void unaRespuestaCorrectaDevuelveLaSesionAbierta() {
        stripe.stubFor(post(urlEqualTo(RUTA)).willReturn(okJson(SESION)));

        SesionPago sesion = crear();

        assertThat(sesion.referencia()).isEqualTo("cs_test_1");
        assertThat(sesion.abierta()).isTrue();
        assertThat(sesion.url()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_1");
        assertThat(esperas).isEmpty();
    }

    @Test
    void un429SeReintentaConLaMismaClaveYEsperaExponencial() {
        stripe.stubFor(post(urlEqualTo(RUTA)).inScenario("limite").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withBody(error("invalid_request_error", "rate_limit")))
                .willSetStateTo("segundo"));
        stripe.stubFor(post(urlEqualTo(RUTA)).inScenario("limite").whenScenarioStateIs("segundo")
                .willReturn(aResponse().withStatus(429).withBody(error("invalid_request_error", "rate_limit")))
                .willSetStateTo("tercero"));
        stripe.stubFor(post(urlEqualTo(RUTA)).inScenario("limite").whenScenarioStateIs("tercero")
                .willReturn(okJson(SESION)));

        assertThat(crear().referencia()).isEqualTo("cs_test_1");

        assertThat(intentos()).isEqualTo(3);
        stripe.verify(3, postRequestedFor(urlEqualTo(RUTA))
                .withHeader("Idempotency-Key", equalTo("recarga-" + movimiento)));
        assertThat(esperas).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
    }

    @Test
    void unErrorDeConexionSeReintentaYSiPersisteResponde502() {
        stripe.stubFor(post(urlEqualTo(RUTA)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(this::crear).isInstanceOf(PasarelaNoDisponibleException.class);

        assertThat(intentos()).isEqualTo(3); // 1 + 2 reintentos
    }

    @Test
    void un409ConcurrenteSeReintentaPeroUnIdempotencyErrorNo() {
        stripe.stubFor(post(urlEqualTo(RUTA)).willReturn(aResponse().withStatus(409)
                .withBody(error("idempotency_error", "idempotency_key_in_use"))));

        assertThatThrownBy(this::crear).isInstanceOf(PasarelaNoDisponibleException.class)
                .hasMessageContaining("idempotency_error");
        assertThat(intentos()).isEqualTo(1);

        stripe.resetAll();
        stripe.stubFor(post(urlEqualTo(RUTA)).inScenario("conflicto").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(409).withBody(error("invalid_request_error", "lock_timeout")))
                .willSetStateTo("libre"));
        stripe.stubFor(post(urlEqualTo(RUTA)).inScenario("conflicto").whenScenarioStateIs("libre")
                .willReturn(okJson(SESION)));
        assertThat(crear().referencia()).isEqualTo("cs_test_1");
        assertThat(intentos()).isEqualTo(2);
    }

    @Test
    void losErroresDeLaSolicitudNoSeReintentan() {
        for (int status : new int[] {400, 401, 403, 404}) {
            stripe.resetAll();
            stripe.stubFor(post(urlEqualTo(RUTA)).willReturn(aResponse().withStatus(status)
                    .withHeader("Request-Id", "req_123").withBody(error("invalid_request_error", "parameter_invalid"))));

            assertThatThrownBy(this::crear).isInstanceOf(PasarelaNoDisponibleException.class)
                    .hasMessageContaining(String.valueOf(status));
            assertThat(intentos()).as("status %d", status).isEqualTo(1);
        }
    }

    @Test
    void un500NoSeRepiteConLaMismaClave() {
        // Stripe guarda la respuesta 500 de la clave: repetirla devolvería el mismo error.
        stripe.stubFor(post(urlEqualTo(RUTA)).willReturn(aResponse().withStatus(500)
                .withBody(error("api_error", "unknown"))));

        assertThatThrownBy(this::crear).isInstanceOf(PasarelaNoDisponibleException.class);
        assertThat(intentos()).isEqualTo(1);
    }

    @Test
    void consultarUnaSesionCerradaNoDevuelveUrlYUnErrorDevuelveVacio() {
        stripe.stubFor(get(urlEqualTo(RUTA + "/cs_test_1")).willReturn(okJson("""
                {"id":"cs_test_1","object":"checkout.session","status":"complete","url":null}""")));

        assertThat(pasarela.consultarSesion("cs_test_1")).get().satisfies(s -> {
            assertThat(s.abierta()).isFalse();
            assertThat(s.url()).isNull();
        });
        stripe.verify(getRequestedFor(urlEqualTo(RUTA + "/cs_test_1")).withoutHeader("Idempotency-Key"));

        stripe.stubFor(get(urlEqualTo(RUTA + "/cs_test_x")).willReturn(aResponse().withStatus(404)
                .withBody(error("invalid_request_error", "resource_missing"))));
        assertThat(pasarela.consultarSesion("cs_test_x")).isEmpty();
    }
}
