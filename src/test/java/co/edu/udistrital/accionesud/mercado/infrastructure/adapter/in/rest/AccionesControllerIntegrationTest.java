package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.application.port.in.ActualizarCotizacionesUseCase;
import co.edu.udistrital.accionesud.mercado.application.service.CotizacionesService;
import co.edu.udistrital.accionesud.mercado.domain.model.PoliticaAntiguedad;
import co.edu.udistrital.accionesud.mercado.domain.port.out.AccionCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.CotizacionCachePort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.MercadoCatalogoPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter.Limite;
import co.edu.udistrital.accionesud.mercado.support.DatosMercadoFalso;
import co.edu.udistrital.accionesud.mercado.support.RelojControlable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios de aceptación de SCRUM-24 sobre la API completa (Spring + MockMvc).
 *
 * <p>El reloj y el proveedor se sustituyen por dobles controlables; el actualizador
 * programado está apagado en el perfil {@code test} y la prueba ejecuta sus ciclos a mano
 * (un ciclo = 5 s simulados) para que el resultado sea determinista.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccionesControllerIntegrationTest {

    /** Lunes 2026-01-05 10:00 en Nueva York: NYSE/NASDAQ abiertos. */
    private static final Instant INICIO =
            ZonedDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();

    private static final List<String> TICKERS_EEUU =
            List.of("AAPL", "MSFT", "JNJ", "JPM", "XOM", "GOOGL", "AMZN", "META", "TSLA", "NVDA");
    /** 10 de EE. UU. + 5 internacionales: los usuarios leen de las cinco bolsas. */
    private static final List<String> TICKERS_TODOS = List.of("AAPL", "MSFT", "JNJ", "JPM", "XOM", "GOOGL",
            "AMZN", "META", "TSLA", "NVDA", "BP", "HSBA", "7203", "6758", "BHP");
    private static final int USUARIOS = 100;
    private static final int CICLOS_POR_MINUTO = 12;

    /**
     * Sustituye las fuentes reales (Alpaca e internacional) por dobles con contador de
     * llamadas. El limitador de Alpaca es el real: 60 % de 200 llamadas/min.
     */
    @TestConfiguration
    static class DoblesConfig {
        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(INICIO);
        }

        @Bean
        LimitadorCuotaTokenBucketAdapter limitadorAlpacaPrueba(RelojControlable reloj) {
            return new LimitadorCuotaTokenBucketAdapter("ALPACA", List.of(Limite.porMinuto(200)), 60, reloj);
        }

        @Bean
        DatosMercadoFalso alpacaFalsa(RelojControlable reloj, LimitadorCuotaTokenBucketAdapter limitadorAlpacaPrueba) {
            return DatosMercadoFalso.alpaca(reloj, limitadorAlpacaPrueba);
        }

        @Bean
        DatosMercadoFalso internacionalFalsa(RelojControlable reloj) {
            return DatosMercadoFalso.internacional(reloj, null);
        }

        @Bean
        @Primary
        CotizacionesService cotizacionesServicePrueba(AccionCatalogoPort acciones, MercadoCatalogoPort mercados,
                                                      CotizacionCachePort cache,
                                                      @Qualifier("alpacaFalsa") DatosMercadoFalso alpaca,
                                                      @Qualifier("internacionalFalsa") DatosMercadoFalso internacional,
                                                      RelojControlable reloj, PoliticaAntiguedad politica) {
            return new CotizacionesService(acciones, mercados, cache, List.of(alpaca, internacional), reloj, politica);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    @Qualifier("alpacaFalsa")
    private DatosMercadoFalso alpaca;
    @Autowired
    @Qualifier("internacionalFalsa")
    private DatosMercadoFalso internacional;
    @Autowired
    private ActualizarCotizacionesUseCase actualizador;
    @Autowired
    private LimitadorCuotaTokenBucketAdapter limitador;

    private static RequestPostProcessor inversionista() {
        return user("inversionista").roles("INVERSIONISTA");
    }

    /** Ejecuta un ciclo del actualizador y avanza 5 s el reloj. */
    private void cicloDelActualizador() {
        actualizador.actualizarEstadosUnidos();
        reloj.avanzar(Duration.ofSeconds(5));
    }

    /** Precarga las 15 acciones de LSE, TSE y ASX (una por ciclo, como en producción). */
    private void precargarInternacionales() {
        for (int i = 0; i < 15; i++) {
            actualizador.actualizarInternacionales();
        }
    }

    @Test
    void lasCincoBolsasTienenCotizacionDesdeLaCache() throws Exception {
        cicloDelActualizador();
        precargarInternacionales();

        for (String ticker : List.of("AAPL", "JPM", "BP", "7203", "BHP")) {
            mockMvc.perform(get("/api/v1/acciones/{t}/cotizacion", ticker).with(inversionista()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ticker").value(ticker))
                    .andExpect(jsonPath("$.actualizadoEn").exists());
        }
        mockMvc.perform(get("/api/v1/acciones/BP/cotizacion").with(inversionista()))
                .andExpect(jsonPath("$.fuente").value("INTERNACIONAL"))
                .andExpect(jsonPath("$.umbralAntiguedadSegundos").value(1800))
                .andExpect(jsonPath("$.desactualizada").value(false));
        mockMvc.perform(get("/api/v1/acciones/AAPL/cotizacion").with(inversionista()))
                .andExpect(jsonPath("$.umbralAntiguedadSegundos").value(15));
        assertThat(internacional.llamadas()).isEqualTo(15);
    }

    @Test
    void cienUsuariosConcurrentesNoAumentanLasLlamadasAAlpaca() throws Exception {
        cicloDelActualizador(); // primer dato en caché
        precargarInternacionales();
        int llamadasInternacionales = internacional.llamadas();

        Map<Integer, LongAdder> respuestasPorEstado = new ConcurrentHashMap<>();
        LongAdder lecturas = new LongAdder();
        AtomicBoolean activo = new AtomicBoolean(true);
        CountDownLatch listos = new CountDownLatch(USUARIOS);
        CountDownLatch arranque = new CountDownLatch(1);
        ExecutorService usuarios = Executors.newFixedThreadPool(USUARIOS);

        for (int u = 0; u < USUARIOS; u++) {
            String ticker = TICKERS_TODOS.get(u % TICKERS_TODOS.size());
            usuarios.submit(() -> {
                listos.countDown();
                arranque.await();
                while (activo.get()) {
                    int codigo = mockMvc.perform(get("/api/v1/acciones/{t}/cotizacion", ticker).with(inversionista()))
                            .andReturn().getResponse().getStatus();
                    respuestasPorEstado.computeIfAbsent(codigo, c -> new LongAdder()).increment();
                    lecturas.increment();
                }
                return null;
            });
        }
        listos.await(10, TimeUnit.SECONDS);
        arranque.countDown();

        // Resto del minuto simulado: 11 ciclos más mientras los 100 usuarios leen sin parar.
        for (int ciclo = 1; ciclo < CICLOS_POR_MINUTO; ciclo++) {
            Thread.sleep(40);
            cicloDelActualizador();
        }
        while (lecturas.sum() < USUARIOS * 10L) {
            Thread.sleep(10);
        }
        activo.set(false);
        usuarios.shutdown();
        assertThat(usuarios.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(lecturas.sum()).isGreaterThanOrEqualTo(USUARIOS * 10L);
        assertThat(respuestasPorEstado).containsOnlyKeys(200);
        // 12 llamadas por minuto, sin importar las miles de lecturas concurrentes.
        assertThat(alpaca.llamadas()).isEqualTo(CICLOS_POR_MINUTO);
        assertThat(internacional.llamadas()).as("las lecturas no consultan proveedores internacionales")
                .isEqualTo(llamadasInternacionales);
        assertThat(limitador.estadisticas().llamadasTotales()).isEqualTo(CICLOS_POR_MINUTO);
        assertThat(limitador.estadisticas().llamadasUltimoMinuto()).isLessThanOrEqualTo(CICLOS_POR_MINUTO);
        assertThat(limitador.estadisticas().llamadasRechazadas()).isZero();
        assertThat(limitador.estadisticas().porcentajeUsoProveedor()).isLessThanOrEqualTo(60.0);
    }

    @Test
    void siAlpacaCaeLasLecturasConcurrentesSiguenFuncionandoDesdeLaCache() throws Exception {
        cicloDelActualizador();
        Instant ultimoDatoBueno = INICIO;

        alpaca.simularCaida(true);
        for (int i = 0; i < 6; i++) {
            cicloDelActualizador(); // 30 s de caída
        }

        AtomicInteger fallos = new AtomicInteger();
        AtomicInteger conAdvertencia = new AtomicInteger();
        ExecutorService usuarios = Executors.newFixedThreadPool(USUARIOS);
        CountDownLatch arranque = new CountDownLatch(1);
        for (int u = 0; u < USUARIOS; u++) {
            String ticker = TICKERS_EEUU.get(u % TICKERS_EEUU.size());
            usuarios.submit(() -> {
                arranque.await();
                var respuesta = mockMvc.perform(get("/api/v1/acciones/{t}/cotizacion", ticker)
                        .with(inversionista())).andReturn().getResponse();
                if (respuesta.getStatus() != 200) {
                    fallos.incrementAndGet();
                } else if (respuesta.getContentAsString().contains("\"PROVEEDOR_NO_DISPONIBLE\"")) {
                    conAdvertencia.incrementAndGet();
                }
                return null;
            });
        }
        arranque.countDown();
        usuarios.shutdown();
        assertThat(usuarios.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(fallos).hasValue(0);
        assertThat(conAdvertencia).hasValue(USUARIOS);

        mockMvc.perform(get("/api/v1/acciones/AAPL/cotizacion").with(inversionista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizadoEn").value(ultimoDatoBueno.toString()))
                .andExpect(jsonPath("$.desactualizada").value(true))
                .andExpect(jsonPath("$.antiguedadSegundos").value(35))
                .andExpect(jsonPath("$.advertencia.codigo").value("PROVEEDOR_NO_DISPONIBLE"));
    }

    @Test
    void cotizacionAlDiaIncluyeTodosLosCamposSinAdvertencia() throws Exception {
        cicloDelActualizador();

        mockMvc.perform(get("/api/v1/acciones/aapl/cotizacion").with(inversionista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.precio").value("101.00"))
                .andExpect(jsonPath("$.bid").value("100.95"))
                .andExpect(jsonPath("$.ask").value("101.05"))
                .andExpect(jsonPath("$.spread").value("0.10"))
                .andExpect(jsonPath("$.volumen").value(1000))
                .andExpect(jsonPath("$.fuente").value("ALPACA_IEX"))
                .andExpect(jsonPath("$.actualizadoEn").value(INICIO.toString()))
                .andExpect(jsonPath("$.antiguedadSegundos").value(5))
                .andExpect(jsonPath("$.desactualizada").value(false))
                .andExpect(jsonPath("$.advertencia").doesNotExist());
    }

    @Test
    void datoConMasDe15SegundosLlevaAdvertenciaDeAntiguedad() throws Exception {
        cicloDelActualizador();
        reloj.avanzar(Duration.ofSeconds(11)); // 16 s desde actualizadoEn

        mockMvc.perform(get("/api/v1/acciones/MSFT/cotizacion").with(inversionista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desactualizada").value(true))
                .andExpect(jsonPath("$.advertencia.codigo").value("DATO_DESACTUALIZADO"));
    }

    @Test
    void sinDatoEnCacheYProveedorCaidoResponde502() throws Exception {
        alpaca.simularCaida(true);
        cicloDelActualizador();

        mockMvc.perform(get("/api/v1/acciones/AAPL/cotizacion").with(inversionista()))
                .andExpect(status().isBadGateway())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.datos.mercado").value("NASDAQ"))
                .andExpect(jsonPath("$.datos.reintentarEnSegundos").value(5))
                .andExpect(jsonPath("$.traceId").exists());

        // El catálogo sigue disponible aunque no haya cotizaciones.
        mockMvc.perform(get("/api/v1/acciones/AAPL").with(inversionista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cotizacion").doesNotExist())
                .andExpect(jsonPath("$.advertencia.codigo").value("SIN_COTIZACION"));
    }

    @Test
    void elRetryAfterDeUnaAccionInternacionalSigueElIntervaloInternacional() throws Exception {
        internacional.simularCaida(true);
        actualizador.actualizarInternacionales();

        // LSE se actualiza cada 60 s (por defecto): reintentar a los 5 s no traería datos nuevos.
        mockMvc.perform(get("/api/v1/acciones/BP/cotizacion").with(inversionista()))
                .andExpect(status().isBadGateway())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.datos.mercado").value("LSE"))
                .andExpect(jsonPath("$.datos.reintentarEnSegundos").value(60));
    }

    @Test
    void listadoConFiltrosYPaginacion() throws Exception {
        cicloDelActualizador();

        mockMvc.perform(get("/api/v1/acciones").with(inversionista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(25))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(20));

        mockMvc.perform(get("/api/v1/acciones")
                        .param("mercado", "nasdaq").param("sector", "Tecnología")
                        .param("q", "INC").param("pagina", "0").param("tamano", "2")
                        .with(inversionista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.contenido[0].mercado").value("NASDAQ"))
                .andExpect(jsonPath("$.contenido[0].sector").value("TECNOLOGIA"))
                .andExpect(jsonPath("$.contenido[0].moneda").value("USD"))
                .andExpect(jsonPath("$.contenido[0].cotizacion.actualizadoEn").exists());

        mockMvc.perform(get("/api/v1/acciones").param("mercado", "LSE").with(inversionista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.contenido[0].advertencia.codigo").value("SIN_COTIZACION"));
    }

    @Test
    void validaParametrosYErrores() throws Exception {
        mockMvc.perform(get("/api/v1/acciones").param("mercado", "XXX").with(inversionista()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/acciones").param("tamano", "500").with(inversionista()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
        mockMvc.perform(get("/api/v1/acciones/ZZZZ").with(inversionista()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ACCION_NO_ENCONTRADA"));
        mockMvc.perform(get("/api/v1/acciones/ZZZZ/cotizacion").with(inversionista()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/acciones"))
                .andExpect(status().isUnauthorized());
    }
}
