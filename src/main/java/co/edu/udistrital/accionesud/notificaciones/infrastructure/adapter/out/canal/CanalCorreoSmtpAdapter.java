package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.canal;

import co.edu.udistrital.accionesud.notificaciones.application.port.out.CanalEnvioPort;
import co.edu.udistrital.accionesud.notificaciones.domain.exception.EnvioFallidoException;
import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Contacto;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

/**
 * Canal EMAIL real por SMTP (SCRUM-51). Usa el mismo {@link JavaMailSender} que el MFA
 * ({@code spring.mail.*}): en desarrollo va a Mailpit y en staging/prod al SMTP configurado.
 *
 * <p>Clasifica los fallos para la política de reintentos:</p>
 * <ul>
 *   <li><b>Permanentes</b> (el envío queda FALLIDO de inmediato): el usuario no tiene correo, la
 *   dirección es inválida o el servidor la rechazó.</li>
 *   <li><b>Temporales</b> (se reintenta): el servidor no responde, falla la autenticación o
 *   cualquier otro error de transporte.</li>
 * </ul>
 */
public class CanalCorreoSmtpAdapter implements CanalEnvioPort {

    static final String PREFIJO_ASUNTO = "Acciones UD: ";
    static final String PIE = """


            --
            Ves todas tus notificaciones en la campana de Acciones UD.
            Puedes elegir qué eventos te llegan por correo en Perfil > Preferencias.""";

    private final JavaMailSender mailSender;
    private final String remitente;

    public CanalCorreoSmtpAdapter(JavaMailSender mailSender, String remitente) {
        this.mailSender = Objects.requireNonNull(mailSender);
        if (remitente == null || remitente.isBlank()) {
            throw new IllegalArgumentException("El remitente de notificaciones es obligatorio");
        }
        this.remitente = remitente.strip();
    }

    @Override
    public CanalNotificacion canal() {
        return CanalNotificacion.EMAIL;
    }

    @Override
    public void enviar(Notificacion notificacion, Contacto contacto) {
        String correo = contacto.correoOpcional()
                .orElseThrow(() -> EnvioFallidoException.permanente("El usuario no tiene correo registrado"));
        validarDireccion(correo);

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(correo);
        mensaje.setSubject(PREFIJO_ASUNTO + notificacion.titulo());
        mensaje.setText(notificacion.mensaje() + PIE);
        try {
            mailSender.send(mensaje);
        } catch (MailParseException | MailPreparationException e) {
            throw EnvioFallidoException.permanente("El correo no se pudo construir: " + e.getMessage(), e);
        } catch (MailAuthenticationException e) {
            throw EnvioFallidoException.temporal("El servidor SMTP rechazó las credenciales", e);
        } catch (MailSendException e) {
            if (direccionRechazada(e)) {
                throw EnvioFallidoException.permanente("El servidor SMTP rechazó la dirección de destino", e);
            }
            throw EnvioFallidoException.temporal("El servidor SMTP no aceptó el correo: " + e.getMessage(), e);
        } catch (MailException e) {
            throw EnvioFallidoException.temporal("Error SMTP: " + e.getMessage(), e);
        }
    }

    private static void validarDireccion(String correo) {
        try {
            new InternetAddress(correo, true).validate();
        } catch (AddressException e) {
            throw EnvioFallidoException.permanente("El correo del usuario no es válido", e);
        }
    }

    /** El servidor respondió que la dirección no existe o no acepta correo (p. ej. 550). */
    private static boolean direccionRechazada(MailSendException e) {
        return e.getFailedMessages().values().stream().anyMatch(CanalCorreoSmtpAdapter::esDireccionInvalida)
                || esDireccionInvalida(e.getCause());
    }

    private static boolean esDireccionInvalida(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SendFailedException fallo && fallo.getInvalidAddresses() != null
                    && fallo.getInvalidAddresses().length > 0) {
                return true;
            }
        }
        return false;
    }
}
