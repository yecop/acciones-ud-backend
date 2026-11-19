package co.edu.udistrital.accionesud.mercado.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Propiedades configurables del catálogo de mercados y su lista de feriados.
 *
 * <p>Se enlazan desde {@code application.yml} bajo el prefijo
 * {@code accionesud.mercados}. Esto permite administrar feriados y horarios
 * sin modificar el código de dominio.</p>
 */
@ConfigurationProperties(prefix = "accionesud.mercados")
public class MercadosProperties {

    /** Lista de feriados configurables por mercado. */
    private List<Feriado> feriados = new ArrayList<>();

    public List<Feriado> getFeriados() {
        return feriados;
    }

    public void setFeriados(List<Feriado> feriados) {
        this.feriados = feriados;
    }

    /** Credenciales y endpoints de los proveedores de datos de mercado. */
    private Proveedores proveedores = new Proveedores();

    /** Configuración de la caché de respuestas de proveedores. */
    private Cache cache = new Cache();

    public Proveedores getProveedores() {
        return proveedores;
    }

    public void setProveedores(Proveedores proveedores) {
        this.proveedores = proveedores;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /** Servicio de cotizaciones con caché y limitador de cuota (SCRUM-24). */
    private Cotizaciones cotizaciones = new Cotizaciones();

    public Cotizaciones getCotizaciones() {
        return cotizaciones;
    }

    public void setCotizaciones(Cotizaciones cotizaciones) {
        this.cotizaciones = cotizaciones;
    }

    public static class Feriado {
        /** Código de mercado (NYSE, NASDAQ, LSE, TSE, ASX). */
        private String mercado;
        /** Fecha del feriado en formato ISO (yyyy-MM-dd). */
        private String fecha;
        /** Descripción del feriado. */
        private String descripcion;

        public String getMercado() {
            return mercado;
        }

        public void setMercado(String mercado) {
            this.mercado = mercado;
        }

        public String getFecha() {
            return fecha;
        }

        public void setFecha(String fecha) {
            this.fecha = fecha;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public void setDescripcion(String descripcion) {
            this.descripcion = descripcion;
        }
    }

    /**
     * Agrupa la configuración de cada proveedor de datos de mercado.
     * Las credenciales llegan desde variables de entorno (ver .env.example);
     * nunca deben escribirse directamente aquí.
     */
    public static class Proveedores {
        private Alpaca alpaca = new Alpaca();
        private AlphaVantage alphaVantage = new AlphaVantage();
        private Finnhub finnhub = new Finnhub();
        private Polygon polygon = new Polygon();

        public Alpaca getAlpaca() {
            return alpaca;
        }

        public void setAlpaca(Alpaca alpaca) {
            this.alpaca = alpaca;
        }

        public AlphaVantage getAlphaVantage() {
            return alphaVantage;
        }

        public void setAlphaVantage(AlphaVantage alphaVantage) {
            this.alphaVantage = alphaVantage;
        }

        public Finnhub getFinnhub() {
            return finnhub;
        }

        public void setFinnhub(Finnhub finnhub) {
            this.finnhub = finnhub;
        }

        public Polygon getPolygon() {
            return polygon;
        }

        public void setPolygon(Polygon polygon) {
            this.polygon = polygon;
        }
    }

    /** Alpaca: cubre NYSE y NASDAQ. */
    public static class Alpaca {
        private String apiKey;
        private String apiSecret;
        private String baseUrl = "https://paper-api.alpaca.markets";
        /** API de datos de mercado (distinta de la API de trading). */
        private String dataUrl = "https://data.alpaca.markets";
        /** Feed de datos: el plan gratuito solo permite IEX. */
        private String feed = "iex";
        /** Límite oficial de llamadas por minuto del plan de Alpaca (gratuito: 200). */
        private long limiteLlamadasPorMinuto = 200;
        /** Tope de diseño como porcentaje del límite (regla SAD: 60 %). */
        private int porcentajeTope = 60;

        public String getDataUrl() {
            return dataUrl;
        }

        public void setDataUrl(String dataUrl) {
            this.dataUrl = dataUrl;
        }

        public String getFeed() {
            return feed;
        }

        public void setFeed(String feed) {
            this.feed = feed;
        }

        public long getLimiteLlamadasPorMinuto() {
            return limiteLlamadasPorMinuto;
        }

        public void setLimiteLlamadasPorMinuto(long limiteLlamadasPorMinuto) {
            this.limiteLlamadasPorMinuto = limiteLlamadasPorMinuto;
        }

        public int getPorcentajeTope() {
            return porcentajeTope;
        }

        public void setPorcentajeTope(int porcentajeTope) {
            this.porcentajeTope = porcentajeTope;
        }

        /** Llamadas por minuto permitidas por el tope de diseño (60 % de 200 = 120). */
        public long topeLlamadasPorMinuto() {
            return Math.max(1, (limiteLlamadasPorMinuto * porcentajeTope) / 100);
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean estaConfigurado() {
            return apiKey != null && !apiKey.isBlank()
                    && apiSecret != null && !apiSecret.isBlank();
        }
    }

    /** Alpha Vantage: cubre LSE, TSE y ASX (proveedor internacional recomendado). */
    public static class AlphaVantage {
        private String apiKey;
        private String baseUrl = "https://www.alphavantage.co";
        /** Plan gratuito: 5 llamadas/minuto. */
        private long limiteLlamadasPorMinuto = 5;
        /** Plan gratuito: 25 llamadas/dia. */
        private long limiteLlamadasPorDia = 25;

        public long getLimiteLlamadasPorMinuto() {
            return limiteLlamadasPorMinuto;
        }

        public void setLimiteLlamadasPorMinuto(long limiteLlamadasPorMinuto) {
            this.limiteLlamadasPorMinuto = limiteLlamadasPorMinuto;
        }

        public long getLimiteLlamadasPorDia() {
            return limiteLlamadasPorDia;
        }

        public void setLimiteLlamadasPorDia(long limiteLlamadasPorDia) {
            this.limiteLlamadasPorDia = limiteLlamadasPorDia;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean estaConfigurado() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /** Finnhub: alternativa internacional. */
    public static class Finnhub {
        private String apiKey;
        private String baseUrl = "https://finnhub.io/api/v1";
        /** Plan gratuito: 60 llamadas/minuto. */
        private long limiteLlamadasPorMinuto = 60;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public long getLimiteLlamadasPorMinuto() {
            return limiteLlamadasPorMinuto;
        }

        public void setLimiteLlamadasPorMinuto(long limiteLlamadasPorMinuto) {
            this.limiteLlamadasPorMinuto = limiteLlamadasPorMinuto;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public boolean estaConfigurado() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /** Polygon.io: alternativa internacional. */
    public static class Polygon {
        private String apiKey;
        private String baseUrl = "https://api.polygon.io";
        /** Plan gratuito: 5 llamadas/minuto. */
        private long limiteLlamadasPorMinuto = 5;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public long getLimiteLlamadasPorMinuto() {
            return limiteLlamadasPorMinuto;
        }

        public void setLimiteLlamadasPorMinuto(long limiteLlamadasPorMinuto) {
            this.limiteLlamadasPorMinuto = limiteLlamadasPorMinuto;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public boolean estaConfigurado() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * Caché de respuestas de los proveedores para no exceder los límites de los
     * planes gratuitos (rate limits) ni golpear las APIs en cada request del widget.
     */
    public static class Cache {
        /** Activa o desactiva la caché. */
        private boolean enabled = true;
        /** Tiempo de vida de cada entrada, en segundos. */
        private long ttlSegundos = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTtlSegundos() {
            return ttlSegundos;
        }

        public void setTtlSegundos(long ttlSegundos) {
            this.ttlSegundos = ttlSegundos;
        }
    }

    /**
     * Servicio de cotizaciones (SCRUM-24): actualizador programado, umbral de
     * antigüedad y almacenamiento de la caché por ticker.
     */
    public static class Cotizaciones {
        /** Intervalo del actualizador programado en milisegundos (5 s → 12 llamadas/min). */
        private long intervaloMs = 5000;
        /** Antigüedad máxima de un dato de EE. UU. antes de advertir, en segundos. */
        private long umbralAntiguedadSegundos = 15;
        /** Activa el actualizador programado (se desactiva en pruebas). */
        private boolean actualizadorHabilitado = true;
        /** Caché de cotizaciones por ticker. */
        private CacheCotizaciones cache = new CacheCotizaciones();
        /** Mercados internacionales (LSE, TSE, ASX). */
        private Internacional internacional = new Internacional();

        public Internacional getInternacional() {
            return internacional;
        }

        public void setInternacional(Internacional internacional) {
            this.internacional = internacional;
        }

        public long getIntervaloMs() {
            return intervaloMs;
        }

        public void setIntervaloMs(long intervaloMs) {
            this.intervaloMs = intervaloMs;
        }

        public long getUmbralAntiguedadSegundos() {
            return umbralAntiguedadSegundos;
        }

        public void setUmbralAntiguedadSegundos(long umbralAntiguedadSegundos) {
            this.umbralAntiguedadSegundos = umbralAntiguedadSegundos;
        }

        public boolean isActualizadorHabilitado() {
            return actualizadorHabilitado;
        }

        public void setActualizadorHabilitado(boolean actualizadorHabilitado) {
            this.actualizadorHabilitado = actualizadorHabilitado;
        }

        public CacheCotizaciones getCache() {
            return cache;
        }

        public void setCache(CacheCotizaciones cache) {
            this.cache = cache;
        }
    }

    /**
     * Cotizaciones de LSE, TSE y ASX: Alpha Vantage (primario) con respaldo Finnhub y
     * Polygon. Los planes gratuitos permiten pocas llamadas, por eso se consulta un ticker
     * por ciclo (el que lleva más tiempo sin actualizarse) y el umbral de antigüedad es mayor.
     */
    public static class Internacional {
        /** Intervalo del actualizador internacional en milisegundos. */
        private long intervaloMs = 60_000;
        /** Antigüedad máxima antes de advertir, en segundos. */
        private long umbralAntiguedadSegundos = 1800;
        /** Tickers por ciclo (los proveedores gratuitos cotizan uno por llamada). */
        private int tickersPorCiclo = 1;
        /** Tope de diseño como porcentaje del límite de cada proveedor internacional. */
        private int porcentajeTope = 60;
        /**
         * Sufijo de símbolo por proveedor y mercado (p. ej. Finnhub, LSE: "BP" → "BP.L").
         * Clave externa: ALPHA_VANTAGE, FINNHUB, POLYGON; clave interna: LSE, TSE, ASX.
         */
        private Map<String, Map<String, String>> sufijos = sufijosPorDefecto();

        private static Map<String, Map<String, String>> sufijosPorDefecto() {
            Map<String, Map<String, String>> mapa = new LinkedHashMap<>();
            mapa.put("ALPHA_VANTAGE", new LinkedHashMap<>(Map.of("LSE", ".LON", "TSE", ".TYO", "ASX", ".AX")));
            mapa.put("FINNHUB", new LinkedHashMap<>(Map.of("LSE", ".L", "TSE", ".T", "ASX", ".AX")));
            mapa.put("POLYGON", new LinkedHashMap<>(Map.of("LSE", "", "TSE", "", "ASX", "")));
            return mapa;
        }

        /** Símbolo del ticker para el proveedor dado (ticker + sufijo del mercado). */
        public String simbolo(String proveedor, String mercado, String ticker) {
            Map<String, String> porMercado = sufijos.getOrDefault(proveedor, Map.of());
            return ticker + porMercado.getOrDefault(mercado, "");
        }

        public long getIntervaloMs() {
            return intervaloMs;
        }

        public void setIntervaloMs(long intervaloMs) {
            this.intervaloMs = intervaloMs;
        }

        public long getUmbralAntiguedadSegundos() {
            return umbralAntiguedadSegundos;
        }

        public void setUmbralAntiguedadSegundos(long umbralAntiguedadSegundos) {
            this.umbralAntiguedadSegundos = umbralAntiguedadSegundos;
        }

        public int getTickersPorCiclo() {
            return tickersPorCiclo;
        }

        public void setTickersPorCiclo(int tickersPorCiclo) {
            this.tickersPorCiclo = tickersPorCiclo;
        }

        public int getPorcentajeTope() {
            return porcentajeTope;
        }

        public void setPorcentajeTope(int porcentajeTope) {
            this.porcentajeTope = porcentajeTope;
        }

        public Map<String, Map<String, String>> getSufijos() {
            return sufijos;
        }

        public void setSufijos(Map<String, Map<String, String>> sufijos) {
            this.sufijos = sufijos;
        }
    }

    /** Almacenamiento de la caché de cotizaciones. */
    public static class CacheCotizaciones {
        /** {@code redis} (por defecto) o {@code memoria} (pruebas / desarrollo sin Redis). */
        private String tipo = "redis";
        /** Prefijo de las claves en Redis: {@code <prefijo><TICKER>}. */
        private String prefijo = "accionesud:cotizacion:";
        /**
         * Vida de cada entrada en Redis, en horas. Es larga a propósito: si Alpaca cae,
         * se debe seguir sirviendo el último dato disponible.
         */
        private long ttlHoras = 168;

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public String getPrefijo() {
            return prefijo;
        }

        public void setPrefijo(String prefijo) {
            this.prefijo = prefijo;
        }

        public long getTtlHoras() {
            return ttlHoras;
        }

        public void setTtlHoras(long ttlHoras) {
            this.ttlHoras = ttlHoras;
        }
    }
}
