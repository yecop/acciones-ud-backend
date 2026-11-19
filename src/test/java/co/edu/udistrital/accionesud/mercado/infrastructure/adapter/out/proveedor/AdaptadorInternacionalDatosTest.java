package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.exception.CuotaAgotadaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.port.out.LimitadorCuotaPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.catalogo.AccionCatalogoAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter.Limite;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http.MercadoHttpClient;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.AlphaVantageProveedor;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.FinnhubProveedor;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.PolygonProveedor;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import co.edu.udistrital.accionesud.mercado.support.RelojControlable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failover Alpha Vantage → Finnhub → Polygon contra un servidor HTTP local que imita las tres APIs.
 */
class AdaptadorInternacionalDatosTest {

    private static final Instant AHORA = Instant.parse("2026-01-05T15:00:00Z");

    private HttpServer servidor;
    private final List<String> rutas = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> respuestaAlphaVantage = new AtomicReference<>();
    private final AtomicReference<String> respuestaFinnhub = new AtomicReference<>();
    private final AtomicReference<String> respuestaPolygon = new AtomicReference<>();
    private MercadosProperties properties;
    private RelojControlable reloj;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/av/query", ex -> responder(ex, respuestaAlphaVantage.get()));
        servidor.createContext("/fh/quote", ex -> responder(ex, respuestaFinnhub.get()));
        servidor.createContext("/pg/v2/aggs/ticker/", ex -> responder(ex, respuestaPolygon.get()));
        servidor.start();

        String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        properties = new MercadosProperties();
        MercadosProperties.Proveedores p = properties.getProveedores();
        p.getAlphaVantage().setApiKey("av");
        p.getAlphaVantage().setBaseUrl(base + "/av");
        p.getFinnhub().setApiKey("fh");
        p.getFinnhub().setBaseUrl(base + "/fh");
        p.getPolygon().setApiKey("pg");
        p.getPolygon().setBaseUrl(base + "/pg");
        reloj = new RelojControlable(AHORA);

        respuestaAlphaVantage.set("""
                {"Global Quote": {"01. symbol": "BP.LON", "05. price": "472.3500", "06. volume": "1500"}}""");
        respuestaFinnhub.set("{\"c\": 3150.5, \"t\": 1767625200}");
        respuestaPolygon.set("{\"results\": [{\"c\": 25.1, \"v\": 99}]}");
    }

    private void responder(HttpExchange intercambio, String cuerpo) throws IOException {
        rutas.add(intercambio.getRequestURI().toString());
        byte[] bytes = (cuerpo == null ? "{}" : cuerpo).getBytes(StandardCharsets.UTF_8);
        int estado = cuerpo == null ? 503 : 200;
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    private AdaptadorInternacionalDatos adaptador(Map<String, LimitadorCuotaPort> limitadores) {
        MercadoHttpClient http = new MercadoHttpClient(new ObjectMapper(), reloj, properties);
        return new AdaptadorInternacionalDatos(
                List.of(new AlphaVantageProveedor(properties, http), new FinnhubProveedor(properties, http),
                        new PolygonProveedor(properties, http)),
                limitadores, new AccionCatalogoAdapter(), properties.getCotizaciones().getInternacional(), reloj);
    }

    private AdaptadorInternacionalDatos adaptador() {
        return adaptador(Map.of());
    }

    @Test
    void usaAlphaVantageComoPrimarioConElSufijoDeLaBolsa() {
        List<Cotizacion> cotizaciones = adaptador().consultarCotizaciones(Set.of("BP"));

        assertThat(rutas).singleElement().asString()
                .startsWith("/av/query?function=GLOBAL_QUOTE&symbol=BP.LON&apikey=av");
        Cotizacion bp = cotizaciones.get(0);
        assertThat(bp.ticker()).isEqualTo("BP");
        assertThat(bp.precio()).isEqualByComparingTo("472.35");
        assertThat(bp.volumen()).isEqualTo(1500);
        assertThat(bp.fuente()).isEqualTo("ALPHA_VANTAGE");
        assertThat(bp.actualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    void siAlphaVantageAgotaSuPlanPasaAFinnhub() {
        // Alpha Vantage responde 200 con "Information" cuando se agota la cuota diaria.
        respuestaAlphaVantage.set("{\"Information\": \"rate limit\"}");

        Cotizacion toyota = adaptador().consultarCotizaciones(Set.of("7203")).get(0);

        assertThat(rutas).hasSize(2);
        assertThat(rutas.get(1)).startsWith("/fh/quote?symbol=7203.T&token=fh");
        assertThat(toyota.precio()).isEqualByComparingTo("3150.5");
        assertThat(toyota.fuente()).isEqualTo("FINNHUB");
    }

    @Test
    void siAlphaVantageYFinnhubFallanUsaPolygon() {
        respuestaAlphaVantage.set(null);   // HTTP 503
        respuestaFinnhub.set("{\"c\": 0}"); // símbolo no cubierto por el plan

        Cotizacion bhp = adaptador().consultarCotizaciones(Set.of("BHP")).get(0);

        assertThat(rutas).hasSize(3);
        assertThat(rutas.get(2)).startsWith("/pg/v2/aggs/ticker/BHP/prev");
        assertThat(bhp.fuente()).isEqualTo("POLYGON");
        assertThat(bhp.volumen()).isEqualTo(99);
    }

    @Test
    void siTodosFallanReportaProveedorNoDisponible() {
        respuestaAlphaVantage.set(null);
        respuestaFinnhub.set(null);
        respuestaPolygon.set(null);

        assertThatThrownBy(() -> adaptador().consultarCotizaciones(Set.of("BP")))
                .isInstanceOf(ProveedorNoDisponibleException.class);
    }

    @Test
    void elLimitadorDeAlphaVantageRespetaEl60PorCientoDeSuCuotaDiaria() {
        // Plan gratuito de Alpha Vantage: 25/día → tope de diseño 15/día. Finnhub sin cuota.
        Map<String, LimitadorCuotaPort> limitadores = Map.of(
                "ALPHA_VANTAGE", new LimitadorCuotaTokenBucketAdapter("ALPHA_VANTAGE",
                        List.of(Limite.porMinuto(1_000), Limite.porDia(25)), 60, reloj),
                "FINNHUB", new LimitadorCuotaTokenBucketAdapter("FINNHUB",
                        List.of(Limite.porMinuto(1)), 1, reloj),
                "POLYGON", new LimitadorCuotaTokenBucketAdapter("POLYGON",
                        List.of(Limite.porMinuto(1)), 1, reloj));
        AdaptadorInternacionalDatos adaptador = adaptador(limitadores);
        // Consumimos la única llamada de Finnhub y de Polygon para dejarlas sin cuota.
        limitadores.get("FINNHUB").intentarConsumir();
        limitadores.get("POLYGON").intentarConsumir();

        for (int i = 0; i < 15; i++) {
            adaptador.consultarCotizaciones(Set.of("BP"));
        }
        assertThatThrownBy(() -> adaptador.consultarCotizaciones(Set.of("BP")))
                .isInstanceOf(CuotaAgotadaException.class);
        assertThat(rutas).hasSize(15);
        assertThat(limitadores.get("ALPHA_VANTAGE").estadisticas().llamadasRechazadas()).isEqualTo(1);
    }

    @Test
    void sinCredencialesNoLlamaANadie() {
        properties.getProveedores().getAlphaVantage().setApiKey("");
        properties.getProveedores().getFinnhub().setApiKey("");
        properties.getProveedores().getPolygon().setApiKey("");

        assertThatThrownBy(() -> adaptador().consultarCotizaciones(Set.of("BP")))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(rutas).isEmpty();
    }
}
