package co.edu.udistrital.accionesud.mercado.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
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

    public Proveedores getProveedores() {
        return proveedores;
    }

    public void setProveedores(Proveedores proveedores) {
        this.proveedores = proveedores;
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
}
