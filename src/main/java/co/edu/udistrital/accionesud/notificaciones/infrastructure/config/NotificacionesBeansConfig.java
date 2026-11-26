package co.edu.udistrital.accionesud.notificaciones.infrastructure.config;

import co.edu.udistrital.accionesud.notificaciones.application.port.out.CanalEnvioPort;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.DestinatarioPort;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.EnvioRepositoryPort;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.NotificacionRepositoryPort;
import co.edu.udistrital.accionesud.notificaciones.application.service.NotificacionesService;
import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.evento.NotificacionesEventosListener;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.canal.CanalCorreoSmtpAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.canal.CanalSimuladoAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia.DestinatarioJdbcAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia.EnvioJdbcAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia.NotificacionJdbcAdapter;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia.NotificacionesEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionLocalAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionSpringAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Wiring del servicio de notificaciones (SCRUM-51).
 *
 * <ul>
 *   <li>Con base de datos: tablas {@code notificacion} y {@code notificacion_envio} (V13), titular
 *   desde {@code cuenta_corretaje} y canales desde las preferencias de SCRUM-22.</li>
 *   <li>Sin base de datos (perfil {@code test}): mismo contrato en memoria.</li>
 *   <li>EMAIL usa SMTP ({@code spring.mail.*}, Mailpit en dev) salvo que
 *   {@code accionesud.notificaciones.canales.email=simulado} (perfil test). SMS y WHATSAPP son
 *   simulados hasta integrar Twilio.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class NotificacionesBeansConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesBeansConfig.class);
    private static final String CANALES = "accionesud.notificaciones.canales";

    /** Repositorios y destinatarios del módulo, JDBC o en memoria según haya base de datos. */
    public record AlmacenNotificaciones(NotificacionRepositoryPort notificaciones, EnvioRepositoryPort envios,
                                        DestinatarioPort destinatarios) {
    }

    @Bean
    public AlmacenNotificaciones almacenNotificaciones(ObjectProvider<JdbcTemplate> jdbc) {
        JdbcTemplate plantilla = jdbc.getIfUnique();
        if (plantilla == null) {
            log.info("Sin base de datos: las notificaciones se guardan en memoria");
            NotificacionesEnMemoriaAdapter memoria = new NotificacionesEnMemoriaAdapter();
            return new AlmacenNotificaciones(memoria, memoria, memoria);
        }
        return new AlmacenNotificaciones(new NotificacionJdbcAdapter(plantilla), new EnvioJdbcAdapter(plantilla),
                new DestinatarioJdbcAdapter(plantilla));
    }

    @Bean
    public CanalSimuladoAdapter canalSmsSimulado() {
        return new CanalSimuladoAdapter(CanalNotificacion.SMS);
    }

    @Bean
    @ConditionalOnProperty(prefix = CANALES, name = "email", havingValue = "simulado")
    public CanalSimuladoAdapter canalEmailSimulado() {
        log.warn("Canal EMAIL de notificaciones SIMULADO ({}.email=simulado)", CANALES);
        return new CanalSimuladoAdapter(CanalNotificacion.EMAIL);
    }

    @Bean
    @ConditionalOnProperty(prefix = CANALES, name = "email", havingValue = "smtp", matchIfMissing = true)
    public CanalCorreoSmtpAdapter canalEmailSmtp(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${accionesud.notificaciones.correo.remitente:no-reply@accionesud.local}") String remitente) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("El canal EMAIL por SMTP necesita spring.mail.host; use "
                    + CANALES + ".email=simulado si no hay servidor de correo");
        }
        return new CanalCorreoSmtpAdapter(sender, remitente);
    }

    @Bean
    public CanalSimuladoAdapter canalWhatsappSimulado() {
        return new CanalSimuladoAdapter(CanalNotificacion.WHATSAPP);
    }

    @Bean
    public NotificacionesService notificacionesService(
            AlmacenNotificaciones almacen,
            List<CanalEnvioPort> canales,
            ObjectProvider<PlatformTransactionManager> gestores,
            @Value("${accionesud.notificaciones.reintento.max-intentos:5}") int maxIntentos,
            @Value("${accionesud.notificaciones.reintento.espera-base-segundos:30}") long esperaBase,
            @Value("${accionesud.notificaciones.reintento.reserva-segundos:120}") long reserva,
            @Value("${accionesud.notificaciones.reintento.lote:50}") int lote) {
        return new NotificacionesService(almacen.notificaciones(), almacen.envios(), almacen.destinatarios(), canales,
                transaccionPropia(gestores.getIfUnique()),
                new NotificacionesService.PoliticaReintento(maxIntentos, Duration.ofSeconds(esperaBase),
                        Duration.ofSeconds(reserva), lote),
                Clock.systemUTC());
    }

    @Bean
    public NotificacionesEventosListener notificacionesEventosListener(
            NotificacionesService servicio,
            @Value("${accionesud.notificaciones.reintento.habilitado:true}") boolean reintentoHabilitado) {
        return new NotificacionesEventosListener(servicio, servicio, reintentoHabilitado);
    }

    /**
     * Los eventos llegan en el {@code afterCommit} de la transacción que los publicó; ahí una
     * transacción REQUIRED se uniría a la ya confirmada y no guardaría nada. Por eso el módulo
     * abre siempre una transacción nueva (REQUIRES_NEW).
     */
    private static TransaccionPort transaccionPropia(PlatformTransactionManager gestor) {
        if (gestor == null) {
            return new TransaccionLocalAdapter();
        }
        TransactionTemplate plantilla = new TransactionTemplate(gestor);
        plantilla.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new TransaccionSpringAdapter(plantilla);
    }
}
