package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http.MercadoHttpClient;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Estrategia de proveedor de datos basada en Alpaca.
 *
 * <p>Cubre los mercados estadounidenses NYSE y NASDAQ consultando el endpoint
 * {@code GET /v2/clock}, que indica si el mercado está abierto. La derivación de
 * próximas aperturas/cierres se calcula en el dominio; aquí solo se reporta el
 * estado abierto/cerrado para detectar cierres no planeados.</p>
 *
 * <p>Las credenciales se inyectan desde {@link MercadosProperties} (variables de
 * entorno). Si no están configuradas, el adaptador no fuerza estado y delega el
 * cálculo al horario del dominio.</p>
 */
@Component
public class AlpacaProveedorAdapter implements ProveedorDatosMercadoPort {

    private static final Logger log = LoggerFactory.getLogger(AlpacaProveedorAdapter.class);

    private final MercadosProperties.Alpaca config;
    private final MercadoHttpClient httpClient;

    public AlpacaProveedorAdapter(MercadosProperties properties, MercadoHttpClient httpClient) {
        this.config = properties.getProveedores().getAlpaca();
        this.httpClient = httpClient;
        if (!config.estaConfigurado()) {
            log.warn("Alpaca no tiene credenciales configuradas (ALPACA_API_KEY/ALPACA_API_SECRET); "
                    + "el estado de NYSE/NASDAQ se calculará solo por horario.");
        }
    }

    @Override
    public String nombre() {
        return "ALPACA";
    }

    @Override
    public Set<CodigoMercado> mercadosSoportados() {
        return Set.of(CodigoMercado.NYSE, CodigoMercado.NASDAQ);
    }

    @Override
    public Optional<EstadoReportado> consultarEstadoReportado(CodigoMercado codigo) {
        if (!config.estaConfigurado()) {
            return Optional.empty();
        }
        String url = config.getBaseUrl() + "/v2/clock";
        Map<String, String> cabeceras = Map.of(
                "APCA-API-KEY-ID", config.getApiKey(),
                "APCA-API-SECRET-KEY", config.getApiSecret());

        return httpClient.getJson(url, cabeceras)
                .filter(json -> json.hasNonNull("is_open"))
                .map(json -> {
                    boolean abierto = json.get("is_open").asBoolean();
                    return new EstadoReportado(abierto,
                            abierto ? "Alpaca: mercado abierto" : "Alpaca: mercado cerrado");
                });
    }
}
