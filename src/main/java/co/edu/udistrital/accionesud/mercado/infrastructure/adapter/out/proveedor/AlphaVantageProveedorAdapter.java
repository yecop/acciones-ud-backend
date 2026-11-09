package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Estrategia de proveedor recomendada para los mercados internacionales
 * (LSE, TSE, ASX) que Alpaca no cubre.
 *
 * <p>Se puede sustituir o complementar con Finnhub o Polygon.io activando la
 * estrategia correspondiente mediante configuración.</p>
 */
@Component
public class AlphaVantageProveedorAdapter implements ProveedorDatosMercadoPort {

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
        // Integración real con Alpha Vantage (Global Market Status) se añadiría aquí.
        return Optional.empty();
    }
}
