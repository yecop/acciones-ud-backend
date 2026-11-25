package co.edu.udistrital.accionesud.shared.infrastructure.config;

import co.edu.udistrital.accionesud.shared.application.port.out.AccesoComisionistaPort;
import co.edu.udistrital.accionesud.shared.application.port.out.AccesoPremiumPort;
import co.edu.udistrital.accionesud.shared.application.port.out.BrokerOrdenesPort;
import co.edu.udistrital.accionesud.shared.application.port.out.PublicadorEventosPort;
import co.edu.udistrital.accionesud.shared.application.port.out.RegistroAuditoriaPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TrazabilidadPort;
import co.edu.udistrital.accionesud.shared.application.service.RegistradorAuditoria;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.auditoria.AuditoriaEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.auditoria.AuditoriaJdbcAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.broker.BrokerOrdenesSimuladoAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.comisionistas.AccesoComisionistaEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.evento.PublicadorEventosTrasCommitAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.premium.AccesoPremiumEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionLocalAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionSpringAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.trazabilidad.TrazabilidadMdcAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Wiring de los puertos compartidos (SCRUM-33).
 *
 * <ul>
 *   <li>Con base de datos (dev, staging, prod): transacción de Spring y auditoría en
 *   {@code auditoria_evento}.</li>
 *   <li>Sin base de datos (perfil {@code test}): transacción local y auditoría en memoria, con
 *   el mismo contrato (eventos y auditoría solo tras commit).</li>
 *   <li>Broker, premium y comisionistas usan adaptadores simulados hasta que SCRUM-43, 54 y 52
 *   registren los reales con {@code accionesud.integraciones.broker=alpaca},
 *   {@code premium=stripe} y {@code comisionistas=jdbc}.</li>
 * </ul>
 */
@Configuration
public class CompartidoBeansConfig {

    private static final Logger log = LoggerFactory.getLogger(CompartidoBeansConfig.class);
    private static final String INTEGRACIONES = "accionesud.integraciones";

    @Bean
    public TrazabilidadPort trazabilidadPort() {
        return new TrazabilidadMdcAdapter();
    }

    @Bean
    public TransaccionPort transaccionPort(ObjectProvider<PlatformTransactionManager> gestores) {
        PlatformTransactionManager gestor = gestores.getIfUnique();
        if (gestor == null) {
            log.info("Sin gestor de transacciones: se usa la transacción local (sin base de datos)");
            return new TransaccionLocalAdapter();
        }
        return new TransaccionSpringAdapter(new TransactionTemplate(gestor));
    }

    @Bean
    public PublicadorEventosPort publicadorEventosPort(ApplicationEventPublisher publicador) {
        return new PublicadorEventosTrasCommitAdapter(publicador);
    }

    @Bean
    public RegistroAuditoriaPort registroAuditoriaPort(ObjectProvider<JdbcTemplate> jdbc, ObjectMapper json) {
        JdbcTemplate plantilla = jdbc.getIfUnique();
        if (plantilla == null) {
            log.info("Sin base de datos: la auditoría compartida se guarda en memoria");
            return new AuditoriaEnMemoriaAdapter();
        }
        return new AuditoriaJdbcAdapter(plantilla, json);
    }

    @Bean
    public RegistradorAuditoria registradorAuditoria(RegistroAuditoriaPort auditoria, TrazabilidadPort trazabilidad) {
        return new RegistradorAuditoria(auditoria, trazabilidad, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = INTEGRACIONES, name = "broker", havingValue = "simulado", matchIfMissing = true)
    public BrokerOrdenesPort brokerOrdenesSimulado() {
        log.warn("Broker de órdenes SIMULADO activo (accionesud.integraciones.broker=simulado)");
        return new BrokerOrdenesSimuladoAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = INTEGRACIONES, name = "premium", havingValue = "simulado", matchIfMissing = true)
    public AccesoPremiumPort accesoPremiumSimulado(
            @Value("${accionesud.integraciones.premium-usuarios:}") String usuariosPremium) {
        List<UUID> ids = Arrays.stream(usuariosPremium.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .toList();
        return new AccesoPremiumEnMemoriaAdapter(ids);
    }

    @Bean
    @ConditionalOnProperty(prefix = INTEGRACIONES, name = "comisionistas", havingValue = "memoria", matchIfMissing = true)
    public AccesoComisionistaPort accesoComisionistaEnMemoria() {
        return new AccesoComisionistaEnMemoriaAdapter();
    }
}
