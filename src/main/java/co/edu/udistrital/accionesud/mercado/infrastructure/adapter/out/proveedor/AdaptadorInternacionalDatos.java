package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor;

import co.edu.udistrital.accionesud.mercado.domain.exception.CuotaAgotadaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.Accion;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.port.out.AccionCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.DatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.LimitadorCuotaPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.ProveedorInternacional;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adaptador de cotizaciones de LSE, TSE y ASX para {@link DatosMercadoPort} (SCRUM-24).
 *
 * <p>Reutiliza la cadena de proveedores de SCRUM-23 con failover:
 * <b>Alpha Vantage</b> (primario) → <b>Finnhub</b> → <b>Polygon</b>. Para cada ticker se
 * prueba el siguiente proveedor si el anterior no está configurado, no tiene cuota
 * (su limitador lo rechaza) o no devuelve un dato utilizable.</p>
 *
 * <p>Cada proveedor tiene su propio limitador de cuota (Token Bucket al 60 % de su plan).
 * Si todos rechazan por cuota se lanza {@link CuotaAgotadaException}; si se intentó al
 * menos uno y ninguno respondió, {@link ProveedorNoDisponibleException}.</p>
 */
public class AdaptadorInternacionalDatos implements DatosMercadoPort {

    public static final String FUENTE = "INTERNACIONAL";

    private static final Logger log = LoggerFactory.getLogger(AdaptadorInternacionalDatos.class);

    private final List<ProveedorInternacional> cadena;
    private final Map<String, LimitadorCuotaPort> limitadores;
    private final AccionCatalogoPort catalogo;
    private final MercadosProperties.Internacional config;
    private final RelojPort reloj;

    public AdaptadorInternacionalDatos(List<ProveedorInternacional> cadena,
                                       Map<String, LimitadorCuotaPort> limitadores,
                                       AccionCatalogoPort catalogo,
                                       MercadosProperties.Internacional config,
                                       RelojPort reloj) {
        this.cadena = List.copyOf(cadena);
        this.limitadores = Map.copyOf(limitadores);
        this.catalogo = catalogo;
        this.config = config;
        this.reloj = reloj;
        if (this.cadena.stream().noneMatch(ProveedorInternacional::estaDisponible)) {
            log.warn("Ningún proveedor internacional configurado (ALPHA_VANTAGE_API_KEY, FINNHUB_API_KEY, "
                    + "POLYGON_API_KEY): LSE, TSE y ASX no tendrán cotizaciones.");
        }
    }

    @Override
    public String fuente() {
        return FUENTE;
    }

    @Override
    public Set<CodigoMercado> mercadosSoportados() {
        return EnumSet.of(CodigoMercado.LSE, CodigoMercado.TSE, CodigoMercado.ASX);
    }

    @Override
    public int maxTickersPorConsulta() {
        return Math.max(1, config.getTickersPorCiclo());
    }

    @Override
    public List<Cotizacion> consultarCotizaciones(Set<String> tickers) {
        List<Cotizacion> cotizaciones = new ArrayList<>();
        boolean algunIntento = false;
        boolean algunaCuotaAgotada = false;

        for (String ticker : tickers) {
            Optional<Accion> accion = catalogo.buscarPorTicker(ticker)
                    .filter(a -> mercadosSoportados().contains(a.mercado()));
            if (accion.isEmpty()) {
                continue;
            }
            for (ProveedorInternacional proveedor : cadena) {
                if (!proveedor.estaDisponible()) {
                    continue;
                }
                LimitadorCuotaPort limitador = limitadores.get(proveedor.nombre());
                if (limitador != null && !limitador.intentarConsumir()) {
                    algunaCuotaAgotada = true;
                    log.debug("{} sin cuota para {}; probando respaldo", proveedor.nombre(), ticker);
                    continue;
                }
                algunIntento = true;
                String simbolo = config.simbolo(proveedor.nombre(), accion.get().mercado().name(), ticker);
                Optional<Cotizacion> cotizacion = proveedor.cotizar(simbolo, ticker, reloj.ahora());
                if (cotizacion.isPresent()) {
                    cotizaciones.add(cotizacion.get());
                    break;
                }
                log.debug("{} no cotizó {} ({}); probando respaldo", proveedor.nombre(), ticker, simbolo);
            }
        }

        if (!cotizaciones.isEmpty()) {
            return cotizaciones;
        }
        if (!algunIntento && algunaCuotaAgotada) {
            throw new CuotaAgotadaException("Los proveedores internacionales agotaron su tope de cuota");
        }
        throw new ProveedorNoDisponibleException(algunIntento
                ? "Ningún proveedor internacional devolvió cotización para " + tickers
                : "No hay proveedores internacionales configurados");
    }
}
