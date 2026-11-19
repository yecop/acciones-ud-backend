package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.exception.CuotaAgotadaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter.Limite;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http.MercadoHttpClient;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import co.edu.udistrital.accionesud.mercado.support.RelojControlable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el adaptador contra un servidor HTTP local que imita la API de snapshots de Alpaca.
 */
class AdaptadorAlpacaDatosTest {

    private static final Instant AHORA = Instant.parse("2026-01-05T15:00:00Z");
    private static final String RESPUESTA = """
            {
              "AAPL": {
                "latestTrade": {"t": "2026-01-05T14:59:59Z", "p": 190.123},
                "latestQuote": {"t": "2026-01-05T14:59:59Z", "bp": 190.10, "ap": 190.15},
                "dailyBar": {"c": 190.0, "v": 1234567}
              },
              "MSFT": {
                "latestTrade": null,
                "latestQuote": {"bp": 410.00, "ap": 410.20},
                "dailyBar": {"c": 409.5, "v": 7654}
              }
            }
            """;

    private HttpServer servidor;
    private final List<String> rutasRecibidas = new CopyOnWriteArrayList<>();
    private final List<Map<String, List<String>>> cabecerasRecibidas = new CopyOnWriteArrayList<>();
    private final AtomicInteger estado = new AtomicInteger(200);
    private AdaptadorAlpacaDatos adaptador;
    private MercadosProperties properties;
    private RelojControlable reloj;
    private MercadoHttpClient http;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v2/stocks/snapshots", intercambio -> {
            rutasRecibidas.add(intercambio.getRequestURI().toString());
            cabecerasRecibidas.add(Map.copyOf(intercambio.getRequestHeaders()));
            byte[] cuerpo = (estado.get() == 200 ? RESPUESTA : "{\"message\":\"error\"}")
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado.get(), cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();

        properties = new MercadosProperties();
        MercadosProperties.Alpaca alpaca = properties.getProveedores().getAlpaca();
        alpaca.setApiKey("clave-prueba");
        alpaca.setApiSecret("secreto-prueba");
        alpaca.setDataUrl("http://127.0.0.1:" + servidor.getAddress().getPort());

        RelojControlable reloj = new RelojControlable(AHORA);
        MercadoHttpClient http = new MercadoHttpClient(new ObjectMapper(), reloj, properties);
        this.reloj = reloj;
        this.http = http;
        adaptador = new AdaptadorAlpacaDatos(properties, http, reloj,
                new LimitadorCuotaTokenBucketAdapter("ALPACA", List.of(Limite.porMinuto(200)), 60, reloj));
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    @Test
    void haceUnaSolaConsultaAgrupadaConFeedIexYCredenciales() {
        Set<String> tickers = new LinkedHashSet<>(List.of("MSFT", "AAPL"));

        List<Cotizacion> cotizaciones = adaptador.consultarCotizaciones(tickers);

        assertThat(rutasRecibidas).hasSize(1);
        assertThat(rutasRecibidas.get(0))
                .isEqualTo("/v2/stocks/snapshots?symbols=AAPL%2CMSFT&feed=iex");
        Map<String, List<String>> cabeceras = cabecerasRecibidas.get(0);
        assertThat(cabeceras.get("Apca-api-key-id")).containsExactly("clave-prueba");
        assertThat(cabeceras.get("Apca-api-secret-key")).containsExactly("secreto-prueba");

        assertThat(cotizaciones).hasSize(2);
        Cotizacion aapl = cotizaciones.stream().filter(c -> c.ticker().equals("AAPL")).findFirst().orElseThrow();
        assertThat(aapl.precio()).isEqualByComparingTo("190.123");
        assertThat(aapl.bid()).isEqualByComparingTo("190.10");
        assertThat(aapl.ask()).isEqualByComparingTo("190.15");
        assertThat(aapl.spread()).isEqualByComparingTo("0.05");
        assertThat(aapl.volumen()).isEqualTo(1_234_567L);
        assertThat(aapl.actualizadoEn()).isEqualTo(AHORA);
        assertThat(aapl.fuente()).isEqualTo("ALPACA_IEX");
    }

    @Test
    void sinOperacionRecienteUsaElPuntoMedioDelQuote() {
        Cotizacion msft = adaptador.consultarCotizaciones(Set.of("MSFT")).get(0);
        assertThat(msft.precio()).isEqualByComparingTo("410.10");
    }

    @Test
    void noReutilizaLaCacheTtlDelClienteHttp() {
        adaptador.consultarCotizaciones(Set.of("AAPL"));
        adaptador.consultarCotizaciones(Set.of("AAPL"));
        assertThat(rutasRecibidas).hasSize(2);
    }

    @Test
    void errorHttpSeReportaComoProveedorNoDisponible() {
        estado.set(503);
        assertThatThrownBy(() -> adaptador.consultarCotizaciones(Set.of("AAPL")))
                .isInstanceOf(ProveedorNoDisponibleException.class);
    }

    @Test
    void sinCredencialesNoLlamaYReportaProveedorNoDisponible() {
        properties.getProveedores().getAlpaca().setApiKey("");
        assertThatThrownBy(() -> adaptador.consultarCotizaciones(Set.of("AAPL")))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(rutasRecibidas).isEmpty();
    }

    @Test
    void sinCuotaNoLlamaAAlpacaYLoInformaComoCuotaAgotada() {
        AdaptadorAlpacaDatos conTopeDeUno = new AdaptadorAlpacaDatos(properties, http, reloj,
                new LimitadorCuotaTokenBucketAdapter("ALPACA", List.of(Limite.porMinuto(1)), 100, reloj));

        conTopeDeUno.consultarCotizaciones(Set.of("AAPL"));
        assertThatThrownBy(() -> conTopeDeUno.consultarCotizaciones(Set.of("AAPL")))
                .isInstanceOf(CuotaAgotadaException.class);
        assertThat(rutasRecibidas).hasSize(1);
    }
}
