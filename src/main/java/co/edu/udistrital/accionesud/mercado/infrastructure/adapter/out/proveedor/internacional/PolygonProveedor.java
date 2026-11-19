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
 * Proveedor internacional de último respaldo: Polygon.io.
 *
 * <p>Usa {@code GET /v1/marketstatus/now?apiKey=<key>}. La cobertura internacional
 * de Polygon es limitada, por lo que se emplea como última opción tras Alpha
 * Vantage y Finnhub. Se lee el estado del campo {@code exchanges} si está presente;
 * en su defecto, el campo {@code market} global.</p>
 */
@Component
public class PolygonProveedor implements ProveedorInternacional {


    private final MercadosProperties.Polygon config;
    private final MercadoHttpClient httpClient;

    public PolygonProveedor(MercadosProperties properties, MercadoHttpClient httpClient) {
        this.config = properties.getProveedores().getPolygon();
        this.httpClient = httpClient;
    }

    @Override
    public String nombre() {
        return "POLYGON";
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
        String url = config.getBaseUrl() + "/v1/marketstatus/now?apiKey=" + config.getApiKey();

        return httpClient.getJson(url, Map.of())
                .flatMap(this::estadoGlobal);
    }

    private Optional<EstadoReportado> estadoGlobal(JsonNode json) {
        JsonNode market = json.get("market");
        if (market == null) {
            return Optional.empty();
        }
        // "open", "closed" o "extended-hours".
        String estado = market.asText().trim();
        boolean abierto = "open".equalsIgnoreCase(estado) || "extended-hours".equalsIgnoreCase(estado);
        return Optional.of(new EstadoReportado(abierto, "Polygon: " + estado));
    }

    /**
     * {@code GET /v2/aggs/ticker/{simbolo}/prev}: cierre y volumen de la sesión anterior.
     * Su cobertura fuera de EE. UU. es limitada; es el último respaldo de la cadena.
     */
    @Override
    public Optional<Cotizacion> cotizar(String simbolo, String ticker, Instant recibidoEn) {
        if (!estaDisponible()) {
            return Optional.empty();
        }
        String url = config.getBaseUrl() + "/v2/aggs/ticker/"
                + URLEncoder.encode(simbolo, StandardCharsets.UTF_8) + "/prev?adjusted=true&apiKey="
                + config.getApiKey();
        return httpClient.getJsonSinCache(url, Map.of())
                .map(json -> json.path("results").path(0))
                .flatMap(barra -> {
                    BigDecimal precio = decimal(barra.path("c"));
                    if (precio == null) {
                        return Optional.empty();
                    }
                    return Optional.of(Cotizacion.de(ticker, precio, null, null,
                            barra.path("v").asLong(0), recibidoEn, nombre()));
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
