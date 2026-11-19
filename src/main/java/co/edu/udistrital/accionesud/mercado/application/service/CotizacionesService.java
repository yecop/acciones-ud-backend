package co.edu.udistrital.accionesud.mercado.application.service;

import co.edu.udistrital.accionesud.mercado.application.dto.AccionConCotizacion;
import co.edu.udistrital.accionesud.mercado.application.dto.FiltroAcciones;
import co.edu.udistrital.accionesud.mercado.application.dto.Pagina;
import co.edu.udistrital.accionesud.mercado.application.port.in.ActualizarCotizacionesUseCase;
import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarAccionesUseCase;
import co.edu.udistrital.accionesud.mercado.domain.exception.AccionNoEncontradaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.CuotaAgotadaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.Accion;
import co.edu.udistrital.accionesud.mercado.domain.model.Advertencia;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.model.CotizacionVigente;
import co.edu.udistrital.accionesud.mercado.domain.model.PoliticaAntiguedad;
import co.edu.udistrital.accionesud.mercado.domain.port.out.AccionCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.CotizacionCachePort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.DatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.MercadoCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de Datos de Mercado con caché (SCRUM-24) para las cinco bolsas.
 *
 * <p><b>Escritura</b>: la invoca solo el actualizador programado. Por cada fuente
 * ({@link DatosMercadoPort}) toma los tickers de sus mercados abiertos —más los que aún no
 * tienen dato, para precargar— y consulta primero los que llevan más tiempo sin
 * actualizarse, hasta el máximo por consulta de la fuente (Alpaca: los 10 de EE. UU. en una
 * sola llamada; internacionales: uno). El resultado se escribe en la caché.</p>
 *
 * <p><b>Lectura</b>: siempre desde la caché, por lo que el número de usuarios concurrentes
 * no afecta las llamadas a los proveedores.</p>
 *
 * <p>Usa {@code java.util.logging} para no acoplar la capa de aplicación a un framework.</p>
 */
public class CotizacionesService implements ConsultarAccionesUseCase, ActualizarCotizacionesUseCase {

    private static final Logger LOG = Logger.getLogger(CotizacionesService.class.getName());
    private static final Set<CodigoMercado> ESTADOS_UNIDOS = EnumSet.of(CodigoMercado.NYSE, CodigoMercado.NASDAQ);

    private final AccionCatalogoPort catalogoAcciones;
    private final MercadoCatalogoPort catalogoMercados;
    private final CotizacionCachePort cache;
    private final List<DatosMercadoPort> fuentes;
    private final Map<CodigoMercado, DatosMercadoPort> fuentePorMercado = new EnumMap<>(CodigoMercado.class);
    private final RelojPort reloj;
    private final PoliticaAntiguedad politica;

    /** Estado de la última consulta de cada fuente (compartido entre hilos lectores). */
    private final Map<String, Boolean> fuenteDisponible = new ConcurrentHashMap<>();

    public CotizacionesService(AccionCatalogoPort catalogoAcciones,
                               MercadoCatalogoPort catalogoMercados,
                               CotizacionCachePort cache,
                               List<DatosMercadoPort> fuentes,
                               RelojPort reloj,
                               PoliticaAntiguedad politica) {
        this.catalogoAcciones = Objects.requireNonNull(catalogoAcciones);
        this.catalogoMercados = Objects.requireNonNull(catalogoMercados);
        this.cache = Objects.requireNonNull(cache);
        this.fuentes = List.copyOf(fuentes);
        this.reloj = Objects.requireNonNull(reloj);
        this.politica = Objects.requireNonNull(politica);
        for (DatosMercadoPort fuente : this.fuentes) {
            for (CodigoMercado mercado : fuente.mercadosSoportados()) {
                DatosMercadoPort previa = fuentePorMercado.putIfAbsent(mercado, fuente);
                if (previa != null) {
                    throw new IllegalArgumentException("El mercado " + mercado + " tiene dos fuentes: "
                            + previa.fuente() + " y " + fuente.fuente());
                }
            }
        }
    }

    // ------------------------------------------------------------------ escritura

    @Override
    public Map<String, ResultadoActualizacion> actualizarEstadosUnidos() {
        return actualizar(true);
    }

    @Override
    public Map<String, ResultadoActualizacion> actualizarInternacionales() {
        return actualizar(false);
    }

    private Map<String, ResultadoActualizacion> actualizar(boolean estadosUnidos) {
        Map<String, ResultadoActualizacion> resultados = new LinkedHashMap<>();
        for (DatosMercadoPort fuente : fuentes) {
            boolean esDeEstadosUnidos = ESTADOS_UNIDOS.containsAll(fuente.mercadosSoportados());
            if (esDeEstadosUnidos == estadosUnidos) {
                resultados.put(fuente.fuente(), actualizarFuente(fuente));
            }
        }
        return resultados;
    }

    private ResultadoActualizacion actualizarFuente(DatosMercadoPort fuente) {
        Instant ahora = reloj.ahora();
        List<Accion> acciones = catalogoAcciones.listar().stream()
                .filter(a -> fuente.mercadosSoportados().contains(a.mercado()))
                .toList();
        Map<String, Cotizacion> enCache = cache.obtenerVarias(acciones.stream().map(Accion::ticker).toList());
        Set<CodigoMercado> abiertos = mercadosAbiertos(fuente.mercadosSoportados(), ahora);

        // Candidatos: mercados abiertos, o sin dato aún (precarga). Primero los más antiguos.
        Set<String> tickers = new LinkedHashSet<>();
        acciones.stream()
                .filter(a -> abiertos.contains(a.mercado()) || !enCache.containsKey(a.ticker()))
                .sorted(Comparator
                        .comparing((Accion a) -> actualizadoEn(enCache.get(a.ticker())))
                        .thenComparing(Accion::ticker))
                .limit(Math.max(1, fuente.maxTickersPorConsulta()))
                .map(Accion::ticker)
                .forEach(tickers::add);
        if (tickers.isEmpty()) {
            return ResultadoActualizacion.OMITIDO_MERCADO_CERRADO;
        }

        try {
            List<Cotizacion> cotizaciones = fuente.consultarCotizaciones(tickers);
            cache.guardarTodas(cotizaciones);
            Boolean anterior = fuenteDisponible.put(fuente.fuente(), true);
            if (Boolean.FALSE.equals(anterior)) {
                LOG.info(() -> "Fuente " + fuente.fuente() + " recuperada; caché actualizada.");
            }
            return ResultadoActualizacion.ACTUALIZADO;
        } catch (CuotaAgotadaException e) {
            return ResultadoActualizacion.OMITIDO_CUOTA_AGOTADA;
        } catch (RuntimeException e) {
            fuenteDisponible.put(fuente.fuente(), false);
            LOG.log(Level.WARNING, "Falla consultando " + fuente.fuente()
                    + "; se seguirá sirviendo el último dato en caché: " + e.getMessage());
            return ResultadoActualizacion.FALLA_PROVEEDOR;
        }
    }

    private static Instant actualizadoEn(Cotizacion cotizacion) {
        return cotizacion == null ? Instant.MIN : cotizacion.actualizadoEn();
    }

    private Set<CodigoMercado> mercadosAbiertos(Set<CodigoMercado> mercados, Instant ahora) {
        Set<CodigoMercado> abiertos = EnumSet.noneOf(CodigoMercado.class);
        for (CodigoMercado codigo : mercados) {
            // Cálculo local por horario (sin gastar llamadas a proveedores).
            catalogoMercados.buscarPorCodigo(codigo)
                    .filter(m -> m.evaluarEstado(ahora).abierto())
                    .ifPresent(m -> abiertos.add(codigo));
        }
        return abiertos;
    }

    // ------------------------------------------------------------------ lectura

    @Override
    public Pagina<AccionConCotizacion> listar(FiltroAcciones filtro, int pagina, int tamano) {
        FiltroAcciones criterio = filtro == null ? FiltroAcciones.sinFiltros() : filtro;
        List<Accion> filtradas = catalogoAcciones.listar().stream()
                .filter(criterio::cumple)
                .sorted(Comparator.comparing(Accion::ticker))
                .toList();
        Pagina<Accion> paginaAcciones = Pagina.de(filtradas, pagina, tamano);

        Map<String, Cotizacion> enCache = cache.obtenerVarias(
                paginaAcciones.contenido().stream().map(Accion::ticker).toList());
        Instant ahora = reloj.ahora();
        return paginaAcciones.map(accion -> combinar(accion, enCache.get(accion.ticker()), ahora));
    }

    @Override
    public AccionConCotizacion obtener(String ticker) {
        Accion accion = buscarAccion(ticker);
        return combinar(accion, cache.obtener(accion.ticker()).orElse(null), reloj.ahora());
    }

    @Override
    public CotizacionVigente obtenerCotizacion(String ticker) {
        Accion accion = buscarAccion(ticker);
        Cotizacion cotizacion = cache.obtener(accion.ticker())
                .orElseThrow(() -> new ProveedorNoDisponibleException(
                        "No hay cotización disponible para " + accion.ticker()
                                + " y el proveedor de datos no ha entregado información.",
                        accion.mercado()));
        return politica.evaluar(accion, cotizacion, reloj.ahora(), proveedorDisponible(accion.mercado()));
    }

    /** {@code true} si la última consulta de la fuente del mercado fue exitosa (o no se ha consultado). */
    public boolean proveedorDisponible(CodigoMercado mercado) {
        DatosMercadoPort fuente = fuentePorMercado.get(mercado);
        return fuente == null || fuenteDisponible.getOrDefault(fuente.fuente(), true);
    }

    // ------------------------------------------------------------------ auxiliares

    private AccionConCotizacion combinar(Accion accion, Cotizacion cotizacion, Instant ahora) {
        if (cotizacion == null) {
            return new AccionConCotizacion(accion, null, new Advertencia(Advertencia.SIN_COTIZACION,
                    "Aún no hay cotización disponible para " + accion.ticker() + "."));
        }
        return new AccionConCotizacion(accion,
                politica.evaluar(accion, cotizacion, ahora, proveedorDisponible(accion.mercado())), null);
    }

    private Accion buscarAccion(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new AccionNoEncontradaException(String.valueOf(ticker));
        }
        return catalogoAcciones.buscarPorTicker(ticker)
                .orElseThrow(() -> new AccionNoEncontradaException(ticker));
    }
}
