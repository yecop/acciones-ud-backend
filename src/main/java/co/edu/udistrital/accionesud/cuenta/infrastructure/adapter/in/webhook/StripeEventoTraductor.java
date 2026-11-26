package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.webhook;

import co.edu.udistrital.accionesud.cuenta.application.dto.EventoPago;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/**
 * Traduce un evento de Stripe (ya verificado) al aviso de la aplicación.
 *
 * <ul>
 *   <li>{@code checkout.session.completed} con {@code payment_status=paid} y
 *   {@code checkout.session.async_payment_succeeded} → PAGADO.</li>
 *   <li>{@code checkout.session.expired} y {@code checkout.session.async_payment_failed} → NO_PAGADO.</li>
 *   <li>Lo demás (incluido un {@code completed} todavía sin pagar) → IGNORADO.</li>
 * </ul>
 */
public final class StripeEventoTraductor {

    public static final String PROVEEDOR = "STRIPE";

    private StripeEventoTraductor() {
    }

    public static EventoPago traducir(JsonNode evento) {
        String id = evento.path("id").asText("");
        if (id.isBlank()) {
            throw new IllegalArgumentException("El evento de Stripe no trae id");
        }
        String tipo = evento.path("type").asText("");
        JsonNode sesion = evento.path("data").path("object");

        EventoPago.Resultado resultado = switch (tipo) {
            case "checkout.session.completed" -> "paid".equals(sesion.path("payment_status").asText())
                    ? EventoPago.Resultado.PAGADO : EventoPago.Resultado.IGNORADO;
            case "checkout.session.async_payment_succeeded" -> EventoPago.Resultado.PAGADO;
            case "checkout.session.expired", "checkout.session.async_payment_failed" ->
                    EventoPago.Resultado.NO_PAGADO;
            default -> EventoPago.Resultado.IGNORADO;
        };
        if (!"checkout.session".equals(sesion.path("object").asText())) {
            resultado = EventoPago.Resultado.IGNORADO;
        }

        String movimiento = sesion.path("metadata").path("movimientoId").asText(null);
        if (movimiento == null || movimiento.isBlank()) {
            movimiento = sesion.path("client_reference_id").asText(null);
        }
        return new EventoPago(PROVEEDOR, id, resultado, sesion.path("id").asText(null), uuid(movimiento),
                sesion.path("amount_total").asLong(-1), sesion.path("currency").asText(null));
    }

    private static UUID uuid(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
