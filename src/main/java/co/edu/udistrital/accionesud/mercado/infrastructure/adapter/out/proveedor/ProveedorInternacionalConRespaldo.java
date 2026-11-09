package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.AlphaVantageProveedor;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.FinnhubProveedor;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.PolygonProveedor;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.ProveedorInternacional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Estrategia para mercados internacionales (LSE, TSE, ASX) con respaldo automático.
 *
 * <p>Alpha Vantage es el proveedor primario; si no está disponible o no responde,
 * se intenta con Finnhub y, por último, con Polygon.io. El primero que devuelva un
 * estado válido gana. Si ninguno responde, se devuelve vacío y el estado se calcula
 * por horario en el dominio.</p>
 */
@Component
public class ProveedorInternacionalConRespaldo implements ProveedorDatosMercadoPort {

    private static final Logger log = LoggerFactory.getLogger(ProveedorInternacionalConRespaldo.class);

    private final List<ProveedorInternacional> cadena;

    public ProveedorInternacionalConRespaldo(AlphaVantageProveedor alphaVantage,
                                             FinnhubProveedor finnhub,
                                             PolygonProveedor polygon) {
        // Orden de prioridad: Alpha Vantage (primario) → Finnhub → Polygon (respaldos).
        this.cadena = List.of(alphaVantage, finnhub, polygon);
        boolean algunoDisponible = cadena.stream().anyMatch(ProveedorInternacional::estaDisponible);
        if (!algunoDisponible) {
            log.warn("Ningún proveedor internacional configurado (Alpha Vantage/Finnhub/Polygon); "
                    + "el estado de LSE/TSE/ASX se calculará solo por horario.");
        }
    }

    @Override
    public String nombre() {
        return "INTERNACIONAL_CON_RESPALDO";
    }

    @Override
    public Set<CodigoMercado> mercadosSoportados() {
        return Set.of(CodigoMercado.LSE, CodigoMercado.TSE, CodigoMercado.ASX);
    }

    @Override
    public Optional<EstadoReportado> consultarEstadoReportado(CodigoMercado codigo) {
        for (ProveedorInternacional proveedor : cadena) {
            if (!proveedor.estaDisponible()) {
                continue;
            }
            Optional<EstadoReportado> estado = proveedor.consultar(codigo);
            if (estado.isPresent()) {
                log.debug("Estado de {} resuelto por {}", codigo, proveedor.nombre());
                return estado;
            }
            log.debug("{} no resolvió {}; intentando respaldo", proveedor.nombre(), codigo);
        }
        return Optional.empty();
    }
}
