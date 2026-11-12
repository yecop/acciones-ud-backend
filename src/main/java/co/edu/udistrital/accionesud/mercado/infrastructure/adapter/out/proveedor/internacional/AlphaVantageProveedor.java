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
}
