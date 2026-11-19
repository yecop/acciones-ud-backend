package co.edu.udistrital.accionesud.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "accionesud.auth")
public record AuthProperties(@DefaultValue Alpaca alpaca, @DefaultValue Reintento reintento) {

    public record Alpaca(
            @DefaultValue("https://broker-api.sandbox.alpaca.markets") String baseUrl,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String apiSecret,
            @DefaultValue("5") int timeoutSegundos) {

        public boolean estaConfigurado() {
            return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
        }
    }

    public record Reintento(
            @DefaultValue("true") boolean habilitado,
            @DefaultValue("60") long intervaloSegundos,
            @DefaultValue("20") int maximoPorCiclo) {
    }
}