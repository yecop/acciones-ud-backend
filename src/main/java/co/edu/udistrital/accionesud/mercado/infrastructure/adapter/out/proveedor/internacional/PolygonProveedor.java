package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional;

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

    private static final String BASE_URL = "https://api.polygon.io";

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
        String url = BASE_URL + "/v1/marketstatus/now?apiKey=" + config.getApiKey();

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
}
