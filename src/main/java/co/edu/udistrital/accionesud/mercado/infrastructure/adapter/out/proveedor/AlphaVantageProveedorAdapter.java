package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Estrategia de proveedor recomendada para los mercados internacionales
 * (LSE, TSE, ASX) que Alpaca no cubre.
 *
 * <p>Se apoya en el endpoint {@code MARKET_STATUS} de Alpha Vantage. Se puede
 * sustituir o complementar con Finnhub o Polygon.io activando la estrategia
 * correspondiente mediante configuración.</p>
 *
 * <p>Las credenciales se inyectan desde {@link MercadosProperties} (variable de
 * entorno ALPHA_VANTAGE_API_KEY). Si no están configuradas, el estado se deriva
 * del horario calculado por el dominio.</p>
 */
@Component
public class AlphaVantageProveedorAdapter implements ProveedorDatosMercadoPort {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageProveedorAdapter.class);

    private final MercadosProperties.AlphaVantage config;

    public AlphaVantageProveedorAdapter(MercadosProperties properties) {
        this.config = properties.getProveedores().getAlphaVantage();
        if (!config.estaConfigurado()) {
            log.warn("Alpha Vantage no tiene credenciales configuradas (ALPHA_VANTAGE_API_KEY); "
                    + "el estado de LSE/TSE/ASX se calculará solo por horario.");
        }
    }

    @Override
    public String nombre() {
        return "ALPHA_VANTAGE";
    }

    @Override
    public Set<CodigoMercado> mercadosSoportados() {
        return Set.of(CodigoMercado.LSE, CodigoMercado.TSE, CodigoMercado.ASX);
    }

    @Override
    public Optional<EstadoReportado> consultarEstadoReportado(CodigoMercado codigo) {
        if (!config.estaConfigurado()) {
            return Optional.empty();
        }
        // Punto de integración: llamar a GET {baseUrl}/query?function=MARKET_STATUS&apikey=...
        // y mapear el estado del mercado correspondiente a EstadoReportado.
        // Mientras no se implemente la llamada HTTP, se delega al cálculo por horario.
        return Optional.empty();
    }
}
