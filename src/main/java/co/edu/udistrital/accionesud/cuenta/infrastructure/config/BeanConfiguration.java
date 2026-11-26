package co.edu.udistrital.accionesud.cuenta.infrastructure.config;

import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarPortafolioUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.out.CuentaFondosPort;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PortafolioRepositoryPort;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PrecioCachePort;
import co.edu.udistrital.accionesud.cuenta.application.service.FondosService;
import co.edu.udistrital.accionesud.cuenta.application.service.PortafolioService;
import co.edu.udistrital.accionesud.cuenta.application.service.RecargasService;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.webhook.StripeFirmaVerificador;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.pagos.PasarelaDeshabilitadaAdapter;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.pagos.StripePasarelaAdapter;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence.CuentaFondosEnMemoriaAdapter;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence.CuentaFondosJdbcAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import co.edu.udistrital.accionesud.shared.application.port.out.PublicadorEventosPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.application.port.out.TrazabilidadPort;
import co.edu.udistrital.accionesud.shared.application.service.RegistradorAuditoria;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Composicion de dependencias (composition root). Aqui la infraestructura
 * ensambla los servicios de aplicacion con sus puertos de salida, manteniendo
 * las capas de dominio y aplicacion libres de anotaciones de Spring.
 *
 * <p>Saldo y fondos (SCRUM-25 y SCRUM-46): con base de datos se usan {@code cuenta_corretaje} y
 * {@code movimiento_fondos}; sin ella (perfil {@code test}) el mismo contrato en memoria.
 * {@link FondosService} implementa a la vez la consulta de saldo, los depósitos y retiros y el
 * historial.</p>
 */
@Configuration
@EnableConfigurationProperties({PlataformaProperties.class, PagosProperties.class})
public class BeanConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BeanConfiguration.class);

    @Bean
    public CuentaFondosPort cuentaFondosPort(ObjectProvider<JdbcTemplate> jdbc) {
        JdbcTemplate plantilla = jdbc.getIfUnique();
        if (plantilla == null) {
            log.info("Sin base de datos: el saldo y los movimientos de fondos se guardan en memoria");
            return new CuentaFondosEnMemoriaAdapter();
        }
        return new CuentaFondosJdbcAdapter(plantilla);
    }

    @Bean
    public FondosService fondosService(CuentaFondosPort fondos,
                                       TransaccionPort transaccion,
                                       RegistradorAuditoria auditoria,
                                       PublicadorEventosPort eventos,
                                       TrazabilidadPort trazabilidad,
                                       PlataformaProperties properties) {
        return new FondosService(fondos, transaccion, auditoria, eventos, trazabilidad, Clock.systemUTC(),
                properties.getSaldoInicialSimulado());
    }

    // ------------------------------------------------------------ recarga con pasarela

    @Bean
    @ConditionalOnProperty(prefix = "accionesud.integraciones", name = "pagos", havingValue = "stripe")
    public PasarelaPagosPort pasarelaStripe(PagosProperties properties, ObjectMapper json) {
        PagosProperties.Stripe stripe = properties.getStripe();
        Duration timeout = Duration.ofSeconds(stripe.getTimeoutSegundos());
        log.info("Recarga con pasarela: Stripe ({})", stripe.getBaseUrl());
        return new StripePasarelaAdapter(HttpClient.newBuilder().connectTimeout(timeout).build(), json,
                stripe.getBaseUrl(), stripe.getSecretKey(), stripe.getUrlExito(), stripe.getUrlCancelacion(),
                timeout);
    }

    @Bean
    @ConditionalOnProperty(prefix = "accionesud.integraciones", name = "pagos", havingValue = "stripe")
    public StripeFirmaVerificador stripeFirmaVerificador(PagosProperties properties) {
        PagosProperties.Stripe stripe = properties.getStripe();
        return new StripeFirmaVerificador(stripe.getWebhookSecret(), Clock.systemUTC(),
                Duration.ofSeconds(stripe.getToleranciaFirmaSegundos()));
    }

    @Bean
    @ConditionalOnExpression("'${accionesud.integraciones.pagos:deshabilitado}' != 'stripe'")
    public PasarelaPagosPort pasarelaDeshabilitada() {
        log.info("Recarga con pasarela deshabilitada (accionesud.integraciones.pagos != stripe)");
        return new PasarelaDeshabilitadaAdapter();
    }

    @Bean
    public RecargasService recargasService(CuentaFondosPort fondos,
                                           PasarelaPagosPort pasarela,
                                           FondosService saldos,
                                           TransaccionPort transaccion,
                                           RegistradorAuditoria auditoria,
                                           PublicadorEventosPort eventos,
                                           TrazabilidadPort trazabilidad) {
        return new RecargasService(fondos, pasarela, saldos, transaccion, auditoria, eventos, trazabilidad,
                Clock.systemUTC());
    }

    @Bean
    public ConsultarPortafolioUseCase consultarPortafolioUseCase(
            PortafolioRepositoryPort portafolioRepository,
            PrecioCachePort precioCache) {
        return new PortafolioService(portafolioRepository, precioCache);
    }
}
