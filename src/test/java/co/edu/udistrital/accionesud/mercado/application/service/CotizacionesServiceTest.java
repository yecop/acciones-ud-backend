package co.edu.udistrital.accionesud.mercado.application.service;

import co.edu.udistrital.accionesud.mercado.application.dto.AccionConCotizacion;
import co.edu.udistrital.accionesud.mercado.application.dto.FiltroAcciones;
import co.edu.udistrital.accionesud.mercado.application.dto.Pagina;
import co.edu.udistrital.accionesud.mercado.application.port.in.ActualizarCotizacionesUseCase.ResultadoActualizacion;
import co.edu.udistrital.accionesud.mercado.domain.exception.AccionNoEncontradaException;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.Advertencia;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.CotizacionVigente;
import co.edu.udistrital.accionesud.mercado.domain.model.PoliticaAntiguedad;
import co.edu.udistrital.accionesud.mercado.domain.model.Sector;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache.InMemoryCotizacionCacheAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.catalogo.AccionCatalogoAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.catalogo.MercadoCatalogoAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter.Limite;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import co.edu.udistrital.accionesud.mercado.support.DatosMercadoFalso;
import co.edu.udistrital.accionesud.mercado.support.RelojControlable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CotizacionesServiceTest {

    /** Lunes 2026-01-05 10:00 en Nueva York: NYSE/NASDAQ abiertos; Londres 15:00 (abierto). */
    private static final Instant NY_Y_LONDRES_ABIERTOS =
            ZonedDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
    /** Domingo 2026-01-04 10:00 en Nueva York (lunes 00:00 en Tokio, 02:00 en Sídney): todo cerrado. */
    private static final Instant TODO_CERRADO =
            ZonedDateTime.of(2026, 1, 4, 10, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();

    private static final Set<String> TICKERS_LSE = Set.of("HSBA", "BP", "GSK", "ULVR", "BARC");

    private RelojControlable reloj;
    private DatosMercadoFalso alpaca;
    private DatosMercadoFalso internacional;
    private InMemoryCotizacionCacheAdapter cache;
    private LimitadorCuotaTokenBucketAdapter limitadorAlpaca;
    private CotizacionesService servicio;

    @BeforeEach
    void preparar() {
        reloj = new RelojControlable(NY_Y_LONDRES_ABIERTOS);
        limitadorAlpaca = new LimitadorCuotaTokenBucketAdapter("ALPACA", List.of(Limite.porMinuto(200)), 60, reloj);
        alpaca = DatosMercadoFalso.alpaca(reloj, limitadorAlpaca);
        internacional = DatosMercadoFalso.internacional(reloj, null);
        cache = new InMemoryCotizacionCacheAdapter();
        servicio = new CotizacionesService(new AccionCatalogoAdapter(),
                new MercadoCatalogoAdapter(new MercadosProperties()), cache, List.of(alpaca, internacional), reloj,
                new PoliticaAntiguedad(Duration.ofSeconds(15), Duration.ofMinutes(30)));
    }

    private ResultadoActualizacion cicloEstadosUnidos() {
        return servicio.actualizarEstadosUnidos().get("ALPACA_IEX");
    }

    private ResultadoActualizacion cicloInternacional() {
        return servicio.actualizarInternacionales().get("INTERNACIONAL");
    }

    // ------------------------------------------------------------- EE. UU.

    @Test
    void unaSolaLlamadaAgrupadaConLosDiezTickersDeEstadosUnidos() {
        assertThat(cicloEstadosUnidos()).isEqualTo(ResultadoActualizacion.ACTUALIZADO);

        assertThat(alpaca.llamadas()).isEqualTo(1);
        assertThat(alpaca.ultimosTickers()).containsExactlyInAnyOrder(
                "AAPL", "MSFT", "JNJ", "JPM", "XOM", "GOOGL", "AMZN", "META", "TSLA", "NVDA");
        assertThat(internacional.llamadas()).as("el ciclo de EE. UU. no toca otras fuentes").isZero();
    }

    @Test
    void lasLecturasNuncaLlamanAlProveedor() {
        cicloEstadosUnidos();
        for (int i = 0; i < 1_000; i++) {
            servicio.obtenerCotizacion("AAPL");
            servicio.obtener("MSFT");
            servicio.listar(FiltroAcciones.sinFiltros(), 0, 20);
        }
        assertThat(alpaca.llamadas()).isEqualTo(1);
        assertThat(internacional.llamadas()).isZero();
    }

    @Test
    void cadaCincoSegundosDuranteUnMinutoSonDoceLlamadas() {
        for (int ciclo = 0; ciclo < 12; ciclo++) {
            cicloEstadosUnidos();
            reloj.avanzar(Duration.ofSeconds(5));
        }
        assertThat(alpaca.llamadas()).isEqualTo(12);
        assertThat(limitadorAlpaca.estadisticas().llamadasRechazadas()).isZero();
    }

    @Test
    void elLimitadorImpideSuperarEl60PorCientoDelLimiteDeAlpaca() {
        // Un actualizador mal configurado que intenta 500 llamadas en el mismo minuto.
        for (int i = 0; i < 500; i++) {
            cicloEstadosUnidos();
        }
        assertThat(alpaca.llamadas()).isEqualTo(120); // 60 % de 200
        assertThat(limitadorAlpaca.estadisticas().llamadasRechazadas()).isEqualTo(380);
        assertThat(limitadorAlpaca.estadisticas().alarmasEmitidas()).isGreaterThanOrEqualTo(1);
        assertThat(cicloEstadosUnidos()).isEqualTo(ResultadoActualizacion.OMITIDO_CUOTA_AGOTADA);
        // La cuota agotada no se confunde con una caída del proveedor.
        assertThat(servicio.obtenerCotizacion("AAPL").advertencia()).isNull();
    }

    @Test
    void conMercadoCerradoNoGastaCuotaSiLaCacheYaTieneDatos() {
        reloj.fijar(TODO_CERRADO);
        assertThat(cicloEstadosUnidos()).as("precarga inicial").isEqualTo(ResultadoActualizacion.ACTUALIZADO);

        reloj.avanzar(Duration.ofSeconds(5));
        assertThat(cicloEstadosUnidos()).isEqualTo(ResultadoActualizacion.OMITIDO_MERCADO_CERRADO);
        assertThat(alpaca.llamadas()).isEqualTo(1);
    }

    @Test
    void siAlpacaCaeSeSirveElUltimoDatoConAdvertencia() {
        cicloEstadosUnidos();
        Instant primerDato = reloj.ahora();

        alpaca.simularCaida(true);
        reloj.avanzar(Duration.ofSeconds(5));
        assertThat(cicloEstadosUnidos()).isEqualTo(ResultadoActualizacion.FALLA_PROVEEDOR);

        CotizacionVigente vigente = servicio.obtenerCotizacion("AAPL");
        assertThat(vigente.cotizacion().actualizadoEn()).isEqualTo(primerDato);
        assertThat(vigente.advertencia().codigo()).isEqualTo(Advertencia.PROVEEDOR_NO_DISPONIBLE);

        alpaca.simularCaida(false);
        reloj.avanzar(Duration.ofSeconds(5));
        cicloEstadosUnidos();
        assertThat(servicio.obtenerCotizacion("AAPL").advertencia()).isNull();
    }

    @Test
    void datoDeEstadosUnidosConMasDe15SegundosLlevaAdvertencia() {
        cicloEstadosUnidos();
        reloj.avanzar(Duration.ofSeconds(16));

        CotizacionVigente vigente = servicio.obtenerCotizacion("AAPL");
        assertThat(vigente.desactualizada()).isTrue();
        assertThat(vigente.umbralAntiguedadSegundos()).isEqualTo(15);
        assertThat(vigente.advertencia().codigo()).isEqualTo(Advertencia.DATO_DESACTUALIZADO);
    }

    // ------------------------------------------------------------- internacionales

    @Test
    void conMercadoCerradoPrecargaTodosLosTickersInternacionalesUnoPorCiclo() {
        reloj.fijar(TODO_CERRADO);
        for (int ciclo = 0; ciclo < 20; ciclo++) {
            cicloInternacional();
            reloj.avanzar(Duration.ofMinutes(1));
        }
        // 15 tickers (LSE, TSE, ASX): una llamada por ticker y luego ya no gasta cuota.
        assertThat(internacional.llamadas()).isEqualTo(15);
        assertThat(internacional.consultas()).allSatisfy(c -> assertThat(c).hasSize(1));
        assertThat(cicloInternacional()).isEqualTo(ResultadoActualizacion.OMITIDO_MERCADO_CERRADO);
        assertThat(servicio.obtenerCotizacion("7203").cotizacion().fuente()).isEqualTo("INTERNACIONAL");
        assertThat(servicio.obtenerCotizacion("BHP")).isNotNull();
    }

    @Test
    void conLondresAbiertoRotaPrimeroLosTickersMasAntiguos() {
        // Precarga de las 15 acciones con todos los mercados cerrados.
        reloj.fijar(TODO_CERRADO);
        for (int ciclo = 0; ciclo < 15; ciclo++) {
            cicloInternacional();
            reloj.avanzar(Duration.ofMinutes(1));
        }
        int llamadasPrecarga = internacional.llamadas();

        // Lunes 15:00 en Londres: solo LSE está abierto (TSE y ASX cerrados).
        reloj.fijar(NY_Y_LONDRES_ABIERTOS);
        Set<String> rotados = new HashSet<>();
        for (int ciclo = 0; ciclo < 5; ciclo++) {
            cicloInternacional();
            rotados.addAll(internacional.ultimosTickers());
            reloj.avanzar(Duration.ofMinutes(1));
        }
        assertThat(internacional.llamadas() - llamadasPrecarga).isEqualTo(5);
        assertThat(rotados).as("cada ticker de LSE una vez, sin repetir").isEqualTo(TICKERS_LSE);
    }

    @Test
    void umbralInternacionalYCaidaDelProveedorInternacional() {
        reloj.fijar(NY_Y_LONDRES_ABIERTOS);
        for (int ciclo = 0; ciclo < 15; ciclo++) {
            cicloInternacional(); // primero los que no tienen dato
        }
        reloj.avanzar(Duration.ofMinutes(10));
        CotizacionVigente bp = servicio.obtenerCotizacion("BP");
        assertThat(bp.umbralAntiguedadSegundos()).isEqualTo(1800);
        assertThat(bp.desactualizada()).as("10 min < 30 min de umbral internacional").isFalse();

        internacional.simularCaida(true);
        assertThat(cicloInternacional()).isEqualTo(ResultadoActualizacion.FALLA_PROVEEDOR);
        assertThat(servicio.obtenerCotizacion("BP").advertencia().codigo())
                .isEqualTo(Advertencia.PROVEEDOR_NO_DISPONIBLE);
        // La caída internacional no afecta la vigencia de EE. UU.
        cicloEstadosUnidos();
        assertThat(servicio.obtenerCotizacion("AAPL").advertencia()).isNull();
    }

    // ------------------------------------------------------------- lectura

    @Test
    void sinDatoEnCacheYProveedorCaidoLanza502() {
        alpaca.simularCaida(true);
        cicloEstadosUnidos();

        assertThatThrownBy(() -> servicio.obtenerCotizacion("AAPL"))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        AccionConCotizacion detalle = servicio.obtener("AAPL");
        assertThat(detalle.cotizacion()).isNull();
        assertThat(detalle.advertencia().codigo()).isEqualTo(Advertencia.SIN_COTIZACION);
    }

    @Test
    void tickerInexistenteLanzaNoEncontrada() {
        assertThatThrownBy(() -> servicio.obtenerCotizacion("ZZZZ"))
                .isInstanceOf(AccionNoEncontradaException.class);
    }

    @Test
    void filtraPorMercadoSectorYTextoYPagina() {
        Pagina<AccionConCotizacion> nasdaqTecnologia = servicio.listar(
                new FiltroAcciones(CodigoMercado.NASDAQ, Sector.TECNOLOGIA, null), 0, 20);
        assertThat(nasdaqTecnologia.contenido()).extracting(a -> a.accion().ticker())
                .containsExactly("AAPL", "GOOGL", "META", "MSFT", "NVDA");

        Pagina<AccionConCotizacion> busqueda = servicio.listar(new FiltroAcciones(null, null, "bank"), 0, 20);
        // "SoftBank" y "Commonwealth Bank": la búsqueda no distingue mayúsculas.
        assertThat(busqueda.contenido()).extracting(a -> a.accion().ticker()).containsExactly("9984", "CBA");

        Pagina<AccionConCotizacion> segunda = servicio.listar(FiltroAcciones.sinFiltros(), 1, 10);
        assertThat(segunda.totalElementos()).isEqualTo(25);
        assertThat(segunda.totalPaginas()).isEqualTo(3);
        assertThat(segunda.contenido()).hasSize(10);

        assertThat(servicio.listar(FiltroAcciones.sinFiltros(), 9, 10).contenido()).isEmpty();
    }

    @Test
    void rechazaDosFuentesParaElMismoMercado() {
        assertThatThrownBy(() -> new CotizacionesService(new AccionCatalogoAdapter(),
                new MercadoCatalogoAdapter(new MercadosProperties()), cache,
                List.of(alpaca, DatosMercadoFalso.alpaca(reloj, null)), reloj,
                new PoliticaAntiguedad(Duration.ofSeconds(15))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
