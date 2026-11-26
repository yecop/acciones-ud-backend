package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.webhook;

import co.edu.udistrital.accionesud.cuenta.application.dto.EventoPago;
import co.edu.udistrital.accionesud.cuenta.application.port.in.RecargarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.in.RecargarSaldoUseCase.Procesamiento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos de Stripe sobre las recargas (SCRUM-46). Es público (Stripe no envía JWT), así que la
 * autenticidad la da la firma {@code Stripe-Signature}: sin firma válida responde 400 y no toca
 * nada. El cuerpo se lee como bytes para verificar exactamente lo que firmó Stripe.
 *
 * <p>Responde 2xx a todo aviso válido, también a los repetidos o ignorados, para que Stripe no lo
 * reintente. Si el procesamiento falla responde 500 y Stripe lo reintenta más tarde.</p>
 */
@RestController
@RequestMapping("/api/v1/pagos/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final RecargarSaldoUseCase recargas;
    private final Optional<StripeFirmaVerificador> verificador;
    private final ObjectMapper json;

    public StripeWebhookController(RecargarSaldoUseCase recargas, ObjectProvider<StripeFirmaVerificador> verificador,
                                   ObjectMapper json) {
        this.recargas = recargas;
        this.verificador = Optional.ofNullable(verificador.getIfAvailable());
        this.json = json;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> recibir(
            @RequestHeader(name = "Stripe-Signature", required = false) String firma,
            @RequestBody(required = false) byte[] cuerpo) {
        if (verificador.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("codigo", "PASARELA_NO_CONFIGURADA"));
        }
        if (!verificador.get().esValida(cuerpo, firma)) {
            log.warn("Aviso de Stripe rechazado: firma ausente, inválida o vencida");
            return ResponseEntity.badRequest().body(Map.of("codigo", "FIRMA_INVALIDA"));
        }
        JsonNode evento;
        EventoPago aviso;
        try {
            evento = json.readTree(cuerpo);
            aviso = StripeEventoTraductor.traducir(evento);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Aviso de Stripe con firma válida pero ilegible: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("codigo", "SOLICITUD_INVALIDA"));
        }
        Procesamiento resultado = recargas.procesar(aviso);
        log.info("Aviso de Stripe {} ({}) → {}", aviso.eventoId(), evento.path("type").asText(), resultado);
        return ResponseEntity.ok(Map.of("recibido", true, "resultado", resultado.name()));
    }
}
