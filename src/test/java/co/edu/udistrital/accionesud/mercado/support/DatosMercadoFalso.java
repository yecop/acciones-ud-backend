package co.edu.udistrital.accionesud.mercado.support;

import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.port.out.DatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.LimitadorCuotaPort;
import co.edu.udistrital.accionesud.mercado.domain.exception.CuotaAgotadaException;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fuente de cotizaciones simulada: cuenta las llamadas "al proveedor", aplica opcionalmente
 * un limitador de cuota real y permite simular una caída.
 */
public class DatosMercadoFalso implements DatosMercadoPort {

    private final String fuente;
    private final Set<CodigoMercado> mercados;
    private final int maxTickers;
    private final RelojPort reloj;
    private final LimitadorCuotaPort limitador;
    private final AtomicInteger llamadas = new AtomicInteger();
    private final AtomicBoolean caido = new AtomicBoolean(false);
    private final List<Set<String>> consultas = new CopyOnWriteArrayList<>();

    public DatosMercadoFalso(String fuente, Set<CodigoMercado> mercados, int maxTickers, RelojPort reloj,
                             LimitadorCuotaPort limitador) {
        this.fuente = fuente;
        this.mercados = EnumSet.copyOf(mercados);
        this.maxTickers = maxTickers;
        this.reloj = reloj;
        this.limitador = limitador;
    }

    /** Alpaca simulada: NYSE y NASDAQ, todos los tickers en una sola llamada. */
    public static DatosMercadoFalso alpaca(RelojPort reloj, LimitadorCuotaPort limitador) {
        return new DatosMercadoFalso("ALPACA_IEX", EnumSet.of(CodigoMercado.NYSE, CodigoMercado.NASDAQ),
                Integer.MAX_VALUE, reloj, limitador);
    }

    /** Fuente internacional simulada: LSE, TSE y ASX, un ticker por llamada. */
    public static DatosMercadoFalso internacional(RelojPort reloj, LimitadorCuotaPort limitador) {
        return new DatosMercadoFalso("INTERNACIONAL", EnumSet.of(CodigoMercado.LSE, CodigoMercado.TSE,
                CodigoMercado.ASX), 1, reloj, limitador);
    }

    @Override
    public String fuente() {
        return fuente;
    }

    @Override
    public Set<CodigoMercado> mercadosSoportados() {
        return mercados;
    }

    @Override
    public int maxTickersPorConsulta() {
        return maxTickers;
    }

    @Override
    public List<Cotizacion> consultarCotizaciones(Set<String> tickers) {
        if (limitador != null && !limitador.intentarConsumir()) {
            throw new CuotaAgotadaException(fuente + " sin cuota");
        }
        int numero = llamadas.incrementAndGet();
        consultas.add(Set.copyOf(tickers));
        if (caido.get()) {
            throw new ProveedorNoDisponibleException(fuente + " simulado: HTTP 503");
        }
        BigDecimal base = new BigDecimal("100.00").add(BigDecimal.valueOf(numero));
        return tickers.stream()
                .map(t -> Cotizacion.de(t, base, base.subtract(new BigDecimal("0.05")),
                        base.add(new BigDecimal("0.05")), 1_000L * numero, reloj.ahora(), fuente))
                .toList();
    }

    public int llamadas() {
        return llamadas.get();
    }

    public List<Set<String>> consultas() {
        return consultas;
    }

    public Set<String> ultimosTickers() {
        return consultas.isEmpty() ? Set.of() : consultas.get(consultas.size() - 1);
    }

    public void simularCaida(boolean caido) {
        this.caido.set(caido);
    }
}
