package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.canal;

import co.edu.udistrital.accionesud.notificaciones.domain.model.Contacto;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.TipoNotificacion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-51: el canal EMAIL entrega por SMTP real a Mailpit (el mismo servidor de docker-compose)
 * y se verifica con su API. Se omite sin Docker en local; el guard del CI exige que se ejecute.
 */
@Testcontainers(disabledWithoutDocker = true)
class CanalCorreoMailpitIntegrationTest {

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.21")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    @Test
    void entregaElCorreoPorSmtp() throws Exception {
        JavaMailSenderImpl smtp = new JavaMailSenderImpl();
        smtp.setHost(MAILPIT.getHost());
        smtp.setPort(MAILPIT.getMappedPort(1025));
        CanalCorreoSmtpAdapter canal = new CanalCorreoSmtpAdapter(smtp, "avisos@accionesud.test");
        String destino = "ana-" + UUID.randomUUID() + "@example.test";

        canal.enviar(Notificacion.nueva(UUID.randomUUID(), UUID.randomUUID(), TipoNotificacion.ORDEN_EJECUTADA,
                "Orden ejecutada", "Tu orden de AAPL se ejecutó: 2 a $190.10.", null, Instant.now(), "trace"),
                new Contacto(destino, null));

        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                        "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1/messages"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode mensajes = new ObjectMapper().readTree(respuesta.body()).path("messages");

        JsonNode recibido = null;
        for (JsonNode m : mensajes) {
            if (destino.equals(m.path("To").path(0).path("Address").asText())) {
                recibido = m;
            }
        }
        assertThat(recibido).as("correo para %s en Mailpit", destino).isNotNull();
        assertThat(recibido.path("Subject").asText()).isEqualTo("Acciones UD: Orden ejecutada");
        assertThat(recibido.path("From").path("Address").asText()).isEqualTo("avisos@accionesud.test");
    }
}
