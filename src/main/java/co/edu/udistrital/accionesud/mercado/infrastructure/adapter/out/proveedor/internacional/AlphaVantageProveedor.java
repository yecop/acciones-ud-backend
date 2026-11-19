package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional;

import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort.EstadoReportado;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http.MercadoHttpClient;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Proveedor internacional primario: Alpha Vantage (endpoint MARKET_STATUS).
 *
 * <p>La respuesta contiene un arreglo {@code markets} con el estado por región;
 * se busca la región correspondiente al mercado y se lee {@code current_status}
 * ("open"/"closed").</p>
 */
@Component
public class AlphaVantageProveedor implements ProveedorInternacional {

    private final MercadosProperties.AlphaVantage config;
    private final MercadoHttpClient httpClient;

    public AlphaVantageProveedor(MercadosProperties properties, MercadoHttpClient httpClient) {
        this.config = properties.getProveedores().getAlphaVantage();
        this.httpClient = httpClient;
    }

    @Override
    public String nombre() {
        return "ALPHA_VANTAGE";
    }

    @Override
    public boolean estaDisponible() {
        return config.estaConfigurado();
    }

    @Override
    public Optional<EstadoReportado> consultar(CodigoMercado codigo) {
        if (!estaDisponible()) {
            return Optional.empty();
        }
        String region = regionDe(codigo);
        if (region == null) {
            return Optional.empty();
        }
        String url = config.getBaseUrl() + "/query?function=MARKET_STATUS&apikey="
                + config.getApiKey();

        return httpClient.getJson(url, Map.of())
                .flatMap(json -> estadoPara(json, region));
    }

    private Optional<EstadoReportado> estadoPara(JsonNode json, String region) {
        JsonNode markets = json.get("markets");
        if (markets == null || !markets.isArray()) {
            return Optional.empty();
        }
        for (JsonNode mercado : markets) {
            JsonNode regionNode = mercado.get("region");
            JsonNode estadoNode = mercado.get("current_status");
            if (regionNode != null && estadoNode != null
                    && region.equalsIgnoreCase(regionNode.asText().trim())) {
                boolean abierto = "open".equalsIgnoreCase(estadoNode.asText().trim());
                return Optional.of(new EstadoReportado(abierto,
                        "Alpha Vantage: " + estadoNode.asText().trim()));
            }
        }
        return Optional.empty();
    }

    /** Mapea el código de mercado a la región usada por Alpha Vantage. */
    private String regionDe(CodigoMercado codigo) {
        return switch (codigo) {
            case LSE -> "United Kingdom";
            case TSE -> "Japan";
            case ASX -> "Australia";
            default -> null;
        };
    }

    /**
     * {@code GET /query?function=GLOBAL_QUOTE&symbol=BP.LON}. Alpha Vantage no entrega bid/ask
     * en este endpoint; si se agota su cuota responde 200 con "Note"/"Information" en lugar de
     * "Global Quote", lo que se trata como falla para pasar al respaldo.
     */
    @Override
    public Optional<Cotizacion> cotizar(String simbolo, String ticker, Instant recibidoEn) {
        if (!estaDisponible()) {
            return Optional.empty();
        }
        String url = config.getBaseUrl() + "/query?function=GLOBAL_QUOTE&symbol="
                + URLEncoder.encode(simbolo, StandardCharsets.UTF_8) + "&apikey=" + config.getApiKey();
        return httpClient.getJsonSinCache(url, Map.of())
                .map(json -> json.path("Global Quote"))
                .flatMap(quote -> {
                    BigDecimal precio = decimal(quote.path("05. price"));
                    if (precio == null) {
                        return Optional.empty();
                    }
                    long volumen = quote.path("06. volume").asLong(0);
                    return Optional.of(Cotizacion.de(ticker, precio, null, null, volumen, recibidoEn, nombre()));
                });
    }

    private static BigDecimal decimal(JsonNode nodo) {
        if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
            return null;
        }
        try {
            BigDecimal valor = new BigDecimal(nodo.asText().trim());
            return valor.signum() > 0 ? valor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
