package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.canal;

import co.edu.udistrital.accionesud.notificaciones.domain.exception.EnvioFallidoException;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Contacto;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.TipoNotificacion;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Construcción del correo y clasificación de fallos SMTP (temporal vs permanente). Sin Mockito. */
class CanalCorreoSmtpAdapterTest {

    private static final Notificacion NOTIFICACION = Notificacion.nueva(UUID.randomUUID(), UUID.randomUUID(),
            TipoNotificacion.ORDEN_EJECUTADA, "Orden ejecutada", "Tu orden de AAPL se ejecutó: 2 a $190.10.", null,
            Instant.parse("2026-11-25T15:00:00Z"), "trace");
    private static final Contacto ANA = new Contacto("ana@example.test", "+573001112233");

    /** Captura los mensajes o lanza el error indicado. */
    static final class ServidorFalso extends JavaMailSenderImpl {
        final List<SimpleMailMessage> enviados = new ArrayList<>();
        MailException error;

        @Override
        public void send(SimpleMailMessage... mensajes) {
            if (error != null) {
                throw error;
            }
            enviados.addAll(List.of(mensajes));
        }
    }

    private final ServidorFalso servidor = new ServidorFalso();
    private final CanalCorreoSmtpAdapter canal = new CanalCorreoSmtpAdapter(servidor, "avisos@accionesud.test");

    private EnvioFallidoException fallo(Contacto contacto) {
        try {
            canal.enviar(NOTIFICACION, contacto);
        } catch (EnvioFallidoException e) {
            return e;
        }
        throw new AssertionError("Se esperaba un fallo de envío");
    }

    @Test
    void construyeElCorreoConRemitenteAsuntoYPie() {
        canal.enviar(NOTIFICACION, ANA);

        assertThat(servidor.enviados).singleElement().satisfies(m -> {
            assertThat(m.getFrom()).isEqualTo("avisos@accionesud.test");
            assertThat(m.getTo()).containsExactly("ana@example.test");
            assertThat(m.getSubject()).isEqualTo("Acciones UD: Orden ejecutada");
            assertThat(m.getText()).startsWith("Tu orden de AAPL se ejecutó").contains("Perfil > Preferencias");
        });
    }

    @Test
    void sinCorreoODireccionInvalidaEsPermanente() {
        assertThat(fallo(new Contacto(null, "+573001112233")).reintentable()).isFalse();
        assertThat(fallo(new Contacto("  ", null)).reintentable()).isFalse();
        assertThat(fallo(new Contacto("no es un correo", null)).reintentable()).isFalse();
        assertThat(servidor.enviados).isEmpty();
    }

    @Test
    void unaDireccionRechazadaPorElServidorEsPermanente() throws Exception {
        SendFailedException rechazo = new SendFailedException("550 mailbox unavailable", null, new InternetAddress[0],
                new InternetAddress[0], new InternetAddress[]{new InternetAddress("ana@example.test")});
        servidor.error = new MailSendException(Map.of(new Object(), rechazo));

        EnvioFallidoException e = fallo(ANA);
        assertThat(e.reintentable()).isFalse();
        assertThat(e.getCodigo()).isEqualTo("ENVIO_RECHAZADO");
    }

    @Test
    void autenticacionYErroresDeTransporteSonTemporales() {
        servidor.error = new MailAuthenticationException("535 credenciales inválidas");
        assertThat(fallo(ANA).reintentable()).isTrue();

        servidor.error = new MailSendException("421 servicio no disponible");
        EnvioFallidoException e = fallo(ANA);
        assertThat(e.reintentable()).isTrue();
        assertThat(e.getCodigo()).isEqualTo("ENVIO_FALLIDO");
    }

    @Test
    void unServidorSmtpQueNoRespondeEsTemporal() throws IOException {
        int puerto;
        try (ServerSocket libre = new ServerSocket(0)) {
            puerto = libre.getLocalPort();
        }
        JavaMailSenderImpl real = new JavaMailSenderImpl();
        real.setHost("127.0.0.1");
        real.setPort(puerto);
        real.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
        real.getJavaMailProperties().put("mail.smtp.timeout", "2000");

        assertThatThrownBy(() -> new CanalCorreoSmtpAdapter(real, "avisos@accionesud.test").enviar(NOTIFICACION, ANA))
                .isInstanceOfSatisfying(EnvioFallidoException.class, f -> assertThat(f.reintentable()).isTrue());
    }

    @Test
    void exigeRemitente() {
        assertThatThrownBy(() -> new CanalCorreoSmtpAdapter(servidor, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
