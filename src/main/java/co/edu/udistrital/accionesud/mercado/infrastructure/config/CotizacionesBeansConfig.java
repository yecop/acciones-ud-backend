package co.edu.udistrital.accionesud.mercado.infrastructure.config;

import co.edu.udistrital.accionesud.mercado.application.service.CotizacionesService;
import co.edu.udistrital.accionesud.mercado.domain.model.PoliticaAntiguedad;
import co.edu.udistrital.accionesud.mercado.domain.port.out.AccionCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.CotizacionCachePort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.LimitadorCuotaPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.MercadoCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache.InMemoryCotizacionCacheAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache.RedisCotizacionCacheAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota.LimitadorCuotaTokenBucketAdapter.Limite;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.AdaptadorAlpacaDatos;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.AdaptadorInternacionalDatos;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.http.MercadoHttpClient;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.AlphaVantageProveedor;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.FinnhubProveedor;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.proveedor.internacional.PolygonProveedor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Wiring del Servicio de Datos de Mercado con caché y limitador de cuota (SCRUM-24).
 *
 * <p>Fuentes: Alpaca (NYSE, NASDAQ) e internacional (LSE, TSE, ASX con failover Alpha
 * Vantage → Finnhub → Polygon). Cada proveedor tiene su propio limitador de cuota.</p>
 */
@Configuration
public class CotizacionesBeansConfig {

    private static final String PREFIJO = "accionesud.mercados.cotizaciones";

    @Bean
    public PoliticaAntiguedad politicaAntiguedad(MercadosProperties properties) {
        MercadosProperties.Cotizaciones cotizaciones = properties.getCotizaciones();
        return new PoliticaAntiguedad(
                Duration.ofSeconds(cotizaciones.getUmbralAntiguedadSegundos()),
                Duration.ofSeconds(cotizaciones.getInternacional().getUmbralAntiguedadSegundos()));
    }

    @Bean
    public AdaptadorAlpacaDatos adaptadorAlpacaDatos(MercadosProperties properties, MercadoHttpClient http,
                                                     RelojPort reloj) {
        MercadosProperties.Alpaca alpaca = properties.getProveedores().getAlpaca();
        LimitadorCuotaPort limitador = new LimitadorCuotaTokenBucketAdapter("ALPACA",
                List.of(Limite.porMinuto(alpaca.getLimiteLlamadasPorMinuto())), alpaca.getPorcentajeTope(), reloj);
        return new AdaptadorAlpacaDatos(properties, http, reloj, limitador);
    }

    @Bean
    public AdaptadorInternacionalDatos adaptadorInternacionalDatos(MercadosProperties properties,
                                                                   AlphaVantageProveedor alphaVantage,
                                                                   FinnhubProveedor finnhub,
                                                                   PolygonProveedor polygon,
                                                                   AccionCatalogoPort catalogo,
                                                                   RelojPort reloj) {
        MercadosProperties.Proveedores p = properties.getProveedores();
        MercadosProperties.Internacional internacional = properties.getCotizaciones().getInternacional();
        int tope = internacional.getPorcentajeTope();
        Map<String, LimitadorCuotaPort> limitadores = Map.of(
                alphaVantage.nombre(), new LimitadorCuotaTokenBucketAdapter(alphaVantage.nombre(),
                        List.of(Limite.porMinuto(p.getAlphaVantage().getLimiteLlamadasPorMinuto()),
                                Limite.porDia(p.getAlphaVantage().getLimiteLlamadasPorDia())), tope, reloj),
                finnhub.nombre(), new LimitadorCuotaTokenBucketAdapter(finnhub.nombre(),
                        List.of(Limite.porMinuto(p.getFinnhub().getLimiteLlamadasPorMinuto())), tope, reloj),
                polygon.nombre(), new LimitadorCuotaTokenBucketAdapter(polygon.nombre(),
                        List.of(Limite.porMinuto(p.getPolygon().getLimiteLlamadasPorMinuto())), tope, reloj));
        // Mismo orden de prioridad que SCRUM-23: Alpha Vantage → Finnhub → Polygon.
        return new AdaptadorInternacionalDatos(List.of(alphaVantage, finnhub, polygon), limitadores,
                catalogo, internacional, reloj);
    }

    @Bean
    public CotizacionesService cotizacionesService(AccionCatalogoPort catalogoAcciones,
                                                   MercadoCatalogoPort catalogoMercados,
                                                   CotizacionCachePort cache,
                                                   AdaptadorAlpacaDatos alpaca,
                                                   AdaptadorInternacionalDatos internacional,
                                                   RelojPort reloj,
                                                   PoliticaAntiguedad politica) {
        return new CotizacionesService(catalogoAcciones, catalogoMercados, cache,
                List.of(alpaca, internacional), reloj, politica);
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO + ".cache", name = "tipo", havingValue = "redis", matchIfMissing = true)
    public CotizacionCachePort cotizacionCacheRedis(StringRedisTemplate redis, MercadosProperties properties) {
        MercadosProperties.CacheCotizaciones cache = properties.getCotizaciones().getCache();
        return new RedisCotizacionCacheAdapter(redis, cache.getPrefijo(), Duration.ofHours(cache.getTtlHoras()));
    }

    @Bean
    @ConditionalOnProperty(prefix = PREFIJO + ".cache", name = "tipo", havingValue = "memoria")
    public InMemoryCotizacionCacheAdapter cotizacionCacheMemoria() {
        return new InMemoryCotizacionCacheAdapter();
    }

    /** El planificador solo se activa si el actualizador está habilitado (se apaga en pruebas). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = PREFIJO, name = "actualizador-habilitado", havingValue = "true",
            matchIfMissing = true)
    static class ProgramacionConfig {
    }
}
