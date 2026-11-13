package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.broker;

import co.edu.udistrital.accionesud.auth.application.dto.SolicitudAlta;
import co.edu.udistrital.accionesud.auth.application.port.out.BrokerPort;
import co.edu.udistrital.accionesud.auth.domain.exception.BrokerNoDisponibleException;
import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Alta de cuenta en Alpaca Broker API (sandbox). Los datos que el registro no recoge
 * (dirección, documento fiscal) se completan con valores de ejemplo del sandbox.
 */
public class AlpacaBrokerAdapter implements BrokerPort {

    private final RestClient client;
    private final AuthProperties.Alpaca config;

    public AlpacaBrokerAdapter(RestClient client, AuthProperties.Alpaca config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public String crearCuenta(SolicitudAlta s) {
        if (!config.estaConfigurado()) {
            throw new BrokerNoDisponibleException("Credenciales de Alpaca no configuradas", null);
        }
        try {
            JsonNode respuesta = client.post()
                    .uri("/v1/accounts")
                    .headers(h -> h.setBasicAuth(config.apiKey(), config.apiSecret()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(construirCuerpo(s))
                    .retrieve()
                    .body(JsonNode.class);
            if (respuesta == null || !respuesta.hasNonNull("id")) {
                throw new BrokerNoDisponibleException("Alpaca respondió sin id de cuenta", null);
            }
            return respuesta.get("id").asText();
        } catch (RestClientResponseException e) {
            throw new BrokerNoDisponibleException("Alpaca respondió HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new BrokerNoDisponibleException(
                    "Alpaca no respondió (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private Map<String, Object> construirCuerpo(SolicitudAlta s) {
        String ahora = Instant.now().toString();
        String ip = "185.13.21.99";
        return Map.of(
                "contact", Map.of(
                        "email_address", s.correo(),
                        "phone_number", s.celular() != null ? s.celular() : "555-666-7788",
                        "street_address", List.of("20 N San Mateo Dr"),
                        "city", "San Mateo",
                        "state", "CA",
                        "postal_code", "94401",
                        "country", "USA"),
                "identity", Map.of(
                        "given_name", ascii(s.nombres()),
                        "family_name", ascii(s.apellidos()),
                        "date_of_birth", s.fechaNacimiento().toString(),
                        "tax_id", "661-010-666",
                        "tax_id_type", "USA_SSN",
                        "country_of_citizenship", "USA",
                        "country_of_birth", "USA",
                        "country_of_tax_residence", "USA",
                        "funding_source", List.of("employment_income")),
                "disclosures", Map.of(
                        "is_control_person", false,
                        "is_affiliated_exchange_or_finra", false,
                        "is_politically_exposed", false,
                        "immediate_family_exposed", false),
                "agreements", List.of(
                        Map.of("agreement", "customer_agreement", "signed_at", ahora, "ip_address", ip),
                        Map.of("agreement", "account_agreement", "signed_at", ahora, "ip_address", ip),
                        Map.of("agreement", "margin_agreement", "signed_at", ahora, "ip_address", ip)));
    }

    private static String ascii(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}