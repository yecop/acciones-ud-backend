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
 * Estrategia de proveedor de datos basada en Alpaca.
 *
 * <p>Cubre los mercados estadounidenses NYSE y NASDAQ. La derivación del estado
 * regular (abierto/cerrado) se hace en el dominio a partir del horario y la hora
 * del {@link co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort};
 * aquí se encapsula la posibilidad de reportar cierres no planeados consultando a
 * la API de Alpaca (GET /v2/clock).</p>
 *
 * <p>Las credenciales se inyectan desde {@link MercadosProperties} (variables de
 * entorno). Si no están configuradas, el adaptador no fuerza estado y delega el
 * cálculo al horario del dominio.</p>
 */
@Component
public class AlpacaProveedorAdapter implements ProveedorDatosMercadoPort {

    private static final Logger log = LoggerFactory.getLogger(AlpacaProveedorAdapter.class);

    private final MercadosProperties.Alpaca config;

    public AlpacaProveedorAdapter(MercadosProperties properties) {
        this.config = properties.getProveedores().getAlpaca();
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
        // Punto de integración: llamar a GET {baseUrl}/v2/clock con las cabeceras
        // APCA-API-KEY-ID y APCA-API-SECRET-KEY y mapear la respuesta a EstadoReportado.
        // Mientras no se implemente la llamada HTTP, se delega al cálculo por horario.
        return Optional.empty();
    }
}
