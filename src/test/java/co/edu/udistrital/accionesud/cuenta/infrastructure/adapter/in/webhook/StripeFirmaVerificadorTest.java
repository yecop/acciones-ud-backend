package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StripeFirmaVerificadorTest {

    private static final Instant AHORA = Instant.parse("2026-11-26T18:00:00Z");
    private static final byte[] CUERPO = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}"
            .getBytes(StandardCharsets.UTF_8);

    private final StripeFirmaVerificador verificador = new StripeFirmaVerificador("whsec_prueba",
            Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofMinutes(5));

    @Test
    void aceptaLaFirmaCorrecta() {
        String encabezado = verificador.encabezado(AHORA.getEpochSecond(), CUERPO);

        assertThat(verificador.esValida(CUERPO, encabezado)).isTrue();
    }

    @Test
    void coincideConUnaFirmaCalculadaComoStripe() {
        // HMAC-SHA256("whsec_prueba", "1795716000." + cuerpo), calculado aparte.
        StripeFirmaVerificador fijo = new StripeFirmaVerificador("whsec_prueba",
                Clock.fixed(Instant.ofEpochSecond(1795716000L), ZoneOffset.UTC), Duration.ofMinutes(5));
        String encabezado = fijo.encabezado(1795716000L, CUERPO);

        assertThat(encabezado).startsWith("t=1795716000,v1=").hasSize("t=1795716000,v1=".length() + 64);
        assertThat(fijo.esValida(CUERPO, encabezado)).isTrue();
    }

    @Test
    void aceptaSiAlgunaDeVariasFirmasV1Coincide() {
        String valida = verificador.encabezado(AHORA.getEpochSecond(), CUERPO);
        String encabezado = "t=" + AHORA.getEpochSecond() + ",v1=" + "0".repeat(64) + "," + valida.split(",")[1]
                + ",v0=ignorada";

        assertThat(verificador.esValida(CUERPO, encabezado)).isTrue();
    }

    @Test
    void rechazaUnCuerpoAlterado() {
        String encabezado = verificador.encabezado(AHORA.getEpochSecond(), CUERPO);
        byte[] alterado = "{\"id\":\"evt_1\",\"type\":\"checkout.session.expired\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verificador.esValida(alterado, encabezado)).isFalse();
    }

    @Test
    void rechazaOtroSecreto() {
        StripeFirmaVerificador otro = new StripeFirmaVerificador("whsec_otro",
                Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofMinutes(5));

        assertThat(verificador.esValida(CUERPO, otro.encabezado(AHORA.getEpochSecond(), CUERPO))).isFalse();
    }

    @Test
    void rechazaUnAvisoFueraDeLaTolerancia() {
        long viejo = AHORA.minus(Duration.ofMinutes(6)).getEpochSecond();

        assertThat(verificador.esValida(CUERPO, verificador.encabezado(viejo, CUERPO))).isFalse();
    }

    @Test
    void rechazaEncabezadosIncompletos() {
        assertThat(verificador.esValida(CUERPO, null)).isFalse();
        assertThat(verificador.esValida(CUERPO, "")).isFalse();
        assertThat(verificador.esValida(CUERPO, "v1=abc")).isFalse();
        assertThat(verificador.esValida(CUERPO, "t=" + AHORA.getEpochSecond())).isFalse();
        assertThat(verificador.esValida(CUERPO, "t=no-es-numero,v1=abc")).isFalse();
        assertThat(verificador.esValida(null, verificador.encabezado(AHORA.getEpochSecond(), CUERPO))).isFalse();
    }

    @Test
    void exigeElSecreto() {
        assertThatThrownBy(() -> new StripeFirmaVerificador(" ", Clock.systemUTC(), Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class);
    }
}
