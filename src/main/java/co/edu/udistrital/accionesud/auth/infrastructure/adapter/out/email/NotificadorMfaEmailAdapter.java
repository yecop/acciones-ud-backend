package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.email;

import co.edu.udistrital.accionesud.auth.application.port.out.NotificadorMfaPort;
import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("!test")
public class NotificadorMfaEmailAdapter implements NotificadorMfaPort {

    private final JavaMailSender mailSender;
    private final AuthProperties properties;

    public NotificadorMfaEmailAdapter(JavaMailSender mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void enviarCodigo(String correo, String codigo, Instant expiraEn) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(properties.getCorreo().getRemitente());
        mensaje.setTo(correo);
        mensaje.setSubject(properties.getCorreo().getAsunto());
        mensaje.setText("""
                Tu código de verificación de Acciones UD es: %s

                El código vence en %d segundos.
                """.formatted(codigo,
                properties.getMfa().getExpiracionSegundos()));
        mailSender.send(mensaje);
    }
}
