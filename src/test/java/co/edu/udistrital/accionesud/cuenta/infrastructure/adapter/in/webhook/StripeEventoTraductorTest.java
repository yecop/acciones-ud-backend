package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.udistrital.accionesud.cuenta.application.dto.EventoPago;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StripeEventoTraductorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID MOVIMIENTO = UUID.fromString("7d0f3a52-9a1c-4f7e-8c55-1b2a3c4d5e6f");

    static String evento(String tipo, String pagado, String metadataMovimiento) {
        String metadata = metadataMovimiento == null ? "{}" : "{\"movimientoId\":\"" + metadataMovimiento + "\"}";
        return """
                {"id":"evt_123","type":"%s","data":{"object":{"object":"checkout.session","id":"cs_test_1",
                "payment_status":"%s","amount_total":50000,"currency":"usd",
                "client_reference_id":"%s","metadata":%s}}}""".formatted(tipo, pagado, MOVIMIENTO, metadata);
    }

    private static EventoPago traducir(String cuerpo) throws Exception {
        JsonNode nodo = JSON.readTree(cuerpo);
        return StripeEventoTraductor.traducir(nodo);
    }

    @Test
    void unPagoCompletadoEsPagadoConSusDatos() throws Exception {
        EventoPago e = traducir(evento("checkout.session.completed", "paid", MOVIMIENTO.toString()));

        assertThat(e.proveedor()).isEqualTo("STRIPE");
        assertThat(e.eventoId()).isEqualTo("evt_123");
        assertThat(e.resultado()).isEqualTo(EventoPago.Resultado.PAGADO);
        assertThat(e.referencia()).isEqualTo("cs_test_1");
        assertThat(e.movimientoId()).isEqualTo(MOVIMIENTO);
        assertThat(e.montoCentavos()).isEqualTo(50000);
        assertThat(e.moneda()).isEqualTo("usd");
    }

    @Test
    void unaSesionCompletadaSinPagarTodaviaSeIgnora() throws Exception {
        assertThat(traducir(evento("checkout.session.completed", "unpaid", null)).resultado())
                .isEqualTo(EventoPago.Resultado.IGNORADO);
    }

    @Test
    void pagoAsincronoYVencimiento() throws Exception {
        assertThat(traducir(evento("checkout.session.async_payment_succeeded", "paid", null)).resultado())
                .isEqualTo(EventoPago.Resultado.PAGADO);
        assertThat(traducir(evento("checkout.session.async_payment_failed", "unpaid", null)).resultado())
                .isEqualTo(EventoPago.Resultado.NO_PAGADO);
        assertThat(traducir(evento("checkout.session.expired", "unpaid", null)).resultado())
                .isEqualTo(EventoPago.Resultado.NO_PAGADO);
        assertThat(traducir(evento("payment_intent.created", "unpaid", null)).resultado())
                .isEqualTo(EventoPago.Resultado.IGNORADO);
    }

    @Test
    void sinMetadataUsaClientReferenceId() throws Exception {
        assertThat(traducir(evento("checkout.session.completed", "paid", null)).movimientoId()).isEqualTo(MOVIMIENTO);
    }

    @Test
    void unEventoSinIdNoSeAcepta() {
        assertThatThrownBy(() -> traducir("{\"type\":\"checkout.session.completed\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
