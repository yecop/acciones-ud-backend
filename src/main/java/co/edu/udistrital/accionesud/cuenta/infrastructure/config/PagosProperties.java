package co.edu.udistrital.accionesud.cuenta.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la recarga con pasarela (SCRUM-46), bajo {@code accionesud.pagos}. La pasarela
 * se activa con {@code accionesud.integraciones.pagos=stripe}; las claves llegan por variables de
 * entorno y nunca se versionan.
 */
@ConfigurationProperties(prefix = "accionesud.pagos")
public class PagosProperties {

    private final Stripe stripe = new Stripe();

    public Stripe getStripe() {
        return stripe;
    }

    public static class Stripe {
        /** Clave secreta de Stripe ({@code sk_test_...} o una restringida {@code rk_test_...}). */
        private String secretKey = "";
        /** Secreto de firma del endpoint de webhook ({@code whsec_...}). */
        private String webhookSecret = "";
        /** API de Stripe; se cambia solo en pruebas. */
        private String baseUrl = "https://api.stripe.com";
        /** A dónde vuelve el usuario después de pagar. */
        private String urlExito = "http://localhost:5173/fondos?recarga=exito";
        /** A dónde vuelve el usuario si cancela el pago. */
        private String urlCancelacion = "http://localhost:5173/fondos?recarga=cancelada";
        /** Tiempo máximo de espera por la API de Stripe. */
        private int timeoutSegundos = 10;
        /** Antigüedad máxima aceptada de un aviso firmado (protege contra reenvíos). */
        private long toleranciaFirmaSegundos = 300;

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUrlExito() {
            return urlExito;
        }

        public void setUrlExito(String urlExito) {
            this.urlExito = urlExito;
        }

        public String getUrlCancelacion() {
            return urlCancelacion;
        }

        public void setUrlCancelacion(String urlCancelacion) {
            this.urlCancelacion = urlCancelacion;
        }

        public int getTimeoutSegundos() {
            return timeoutSegundos;
        }

        public void setTimeoutSegundos(int timeoutSegundos) {
            this.timeoutSegundos = timeoutSegundos;
        }

        public long getToleranciaFirmaSegundos() {
            return toleranciaFirmaSegundos;
        }

        public void setToleranciaFirmaSegundos(long toleranciaFirmaSegundos) {
            this.toleranciaFirmaSegundos = toleranciaFirmaSegundos;
        }
    }
}
