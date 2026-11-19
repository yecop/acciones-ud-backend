package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.exception.CuotaAgotadaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.port.out.DatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.LimitadorCuotaPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http.MercadoHttpClient;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Adaptador de salida de Alpaca Market Data para {@link DatosMercadoPort} (SCRUM-24).
 *
 * <p>Hace UNA sola consulta agrupada por ciclo para todos los tickers de EE. UU.:</p>
 * <pre>
 * GET {dataUrl}/v2/stocks/snapshots?symbols=AAPL,AMZN,...&amp;feed=iex
 * </pre>
 * <p>De cada snapshot toma: {@code latestTrade.p} (precio), {@code latestQuote.bp}/{@code ap}
 * (bid/ask) y {@code dailyBar.v} (volumen). {@code actualizadoEn} es el instante en que el
 * backend recibió el dato, que es lo que mide la antigüedad de la caché.</p>
 *
 * <p>Cualquier falla (sin credenciales, timeout, HTTP no 2xx, JSON inválido) se reporta
 * como {@link ProveedorNoDisponibleException} para que el servicio conserve la caché.</p>
 */
public class AdaptadorAlpacaDatos implements DatosMercadoPort {

    public static final String FUENTE = "ALPACA_IEX";

    private static final Logger log = LoggerFactory.getLogger(AdaptadorAlpacaDatos.class);

    private final MercadosProperties.Alpaca config;
    private final MercadoHttpClient httpClient;
    private final RelojPort reloj;
    private final LimitadorCuotaPort limitador;

    public AdaptadorAlpacaDatos(MercadosProperties properties, MercadoHttpClient httpClient, RelojPort reloj,
                                LimitadorCuotaPort limitador) {
        this.config = properties.getProveedores().getAlpaca();
        this.httpClient = httpClient;
        this.reloj = reloj;
        this.limitador = limitador;
    }

    @Override
    public String fuente() {
        return FUENTE;
    }

    @Override
    public Set<CodigoMercado> mercadosSoportados() {
        return EnumSet.of(CodigoMercado.NYSE, CodigoMercado.NASDAQ);
    }

    @Override
    public List<Cotizacion> consultarCotizaciones(Set<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }
        if (!config.estaConfigurado()) {
            throw new ProveedorNoDisponibleException(
                    "Alpaca no tiene credenciales configuradas (ALPACA_API_KEY/ALPACA_API_SECRET)");
        }
        // Toda llamada a Alpaca pasa por el limitador (tope de diseño: 60 % del plan).
        if (!limitador.intentarConsumir()) {
            throw new CuotaAgotadaException("Alpaca: se alcanzó el tope de diseño de llamadas por minuto");
        }

        String url = construirUrl(tickers);
        Map<String, String> cabeceras = Map.of(
                "APCA-API-KEY-ID", config.getApiKey(),
                "APCA-API-SECRET-KEY", config.getApiSecret());

        JsonNode respuesta = httpClient.getJsonSinCache(url, cabeceras)
                .orElseThrow(() -> new ProveedorNoDisponibleException("Alpaca no respondió correctamente"));

        // Alpaca devuelve { "AAPL": {...}, ... }; algunas versiones lo anidan en "snapshots".
        JsonNode snapshots = respuesta.has("snapshots") ? respuesta.get("snapshots") : respuesta;
        Instant recibidoEn = reloj.ahora();
        List<Cotizacion> cotizaciones = new ArrayList<>();
        for (String ticker : tickers) {
            JsonNode snapshot = snapshots.get(ticker);
            if (snapshot == null || snapshot.isNull()) {
                log.debug("Alpaca no devolvió snapshot para {}", ticker);
                continue;
            }
            convertir(ticker, snapshot, recibidoEn).ifPresent(cotizaciones::add);
        }
        if (cotizaciones.isEmpty()) {
            throw new ProveedorNoDisponibleException("Alpaca no devolvió cotizaciones utilizables");
        }
        return cotizaciones;
    }

    private String construirUrl(Set<String> tickers) {
        String simbolos = String.join(",", new TreeSet<>(tickers));
        return config.getDataUrl() + "/v2/stocks/snapshots"
                + "?symbols=" + URLEncoder.encode(simbolos, StandardCharsets.UTF_8)
                + "&feed=" + URLEncoder.encode(config.getFeed(), StandardCharsets.UTF_8);
    }

    private Optional<Cotizacion> convertir(String ticker, JsonNode snapshot, Instant recibidoEn) {
        BigDecimal precio = decimal(snapshot.path("latestTrade").path("p"));
        BigDecimal bid = decimal(snapshot.path("latestQuote").path("bp"));
        BigDecimal ask = decimal(snapshot.path("latestQuote").path("ap"));
        if (precio == null) {
            // Sin operaciones recientes en IEX: se usa el punto medio del quote o el cierre diario.
            precio = (bid != null && ask != null && bid.signum() > 0 && ask.signum() > 0)
                    ? bid.add(ask).divide(BigDecimal.valueOf(2))
                    : decimal(snapshot.path("dailyBar").path("c"));
        }
        if (precio == null) {
            log.debug("Snapshot de {} sin precio utilizable", ticker);
            return Optional.empty();
        }
        long volumen = snapshot.path("dailyBar").path("v").asLong(0);
        return Optional.of(Cotizacion.de(ticker, precio, bid, ask, volumen, recibidoEn, FUENTE));
    }

    private static BigDecimal decimal(JsonNode nodo) {
        if (nodo == null || nodo.isMissingNode() || nodo.isNull() || !nodo.isNumber()) {
            return null;
        }
        return nodo.decimalValue();
    }
}
