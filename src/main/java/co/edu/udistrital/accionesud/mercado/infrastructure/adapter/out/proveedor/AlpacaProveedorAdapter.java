package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort;
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
 * la API de Alpaca.</p>
 */
@Component
public class AlpacaProveedorAdapter implements ProveedorDatosMercadoPort {

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
        // Integración real con la API de Alpaca (GET /v2/clock) se añadiría aquí.
        // Por defecto no se fuerza estado y se delega al cálculo por horario.
        return Optional.empty();
    }
}
