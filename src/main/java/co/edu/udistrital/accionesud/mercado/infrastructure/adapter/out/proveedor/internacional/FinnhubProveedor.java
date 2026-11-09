package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort.EstadoReportado;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http.MercadoHttpClient;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Proveedor internacional de respaldo: Finnhub.
 *
 * <p>Usa {@code GET /api/v1/stock/market-status?exchange=<code>} que devuelve
 * {@code { "isOpen": boolean, ... }}. El código de bolsa se mapea desde el mercado.</p>
 */
@Component
public class FinnhubProveedor implements ProveedorInternacional {

    private static final String BASE_URL = "https://finnhub.io/api/v1";

    private final MercadosProperties.Finnhub config;
    private final MercadoHttpClient httpClient;

    public FinnhubProveedor(MercadosProperties properties, MercadoHttpClient httpClient) {
        this.config = properties.getProveedores().getFinnhub();
        this.httpClient = httpClient;
    }

    @Override
    public String nombre() {
        return "FINNHUB";
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
        String exchange = exchangeDe(codigo);
        if (exchange == null) {
            return Optional.empty();
        }
        String url = BASE_URL + "/stock/market-status?exchange=" + exchange
                + "&token=" + config.getApiKey();

        return httpClient.getJson(url, Map.of())
                .filter(json -> json.hasNonNull("isOpen"))
                .map(json -> {
                    boolean abierto = json.get("isOpen").asBoolean();
                    return new EstadoReportado(abierto, "Finnhub: " + (abierto ? "open" : "closed"));
                });
    }

    /** Códigos de bolsa de Finnhub. */
    private String exchangeDe(CodigoMercado codigo) {
        return switch (codigo) {
            case LSE -> "L";
            case TSE -> "T";
            case ASX -> "AX";
            default -> null;
        };
    }
}
