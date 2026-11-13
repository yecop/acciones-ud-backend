package co.edu.udistrital.accionesud.auth.infrastructure.config;

import co.edu.udistrital.accionesud.auth.application.port.in.ReintentarAltasPendientesUseCase;
import co.edu.udistrital.accionesud.auth.application.port.out.AuditoriaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.BrokerPort;
import co.edu.udistrital.accionesud.auth.application.port.out.HashContrasenaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.udistrital.accionesud.auth.application.service.RegistroService;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.scheduler.AltaPendienteScheduler;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.audit.AuditoriaLogAdapter;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.broker.AlpacaBrokerAdapter;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.persistence.UsuarioJdbcRepository;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.security.PasswordEncoderHashAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// Perfil test sin base de datos: estos beans no se crean allí.
@Configuration
@Profile("!test")
@EnableScheduling
public class AuthBeansConfig {

    @Bean
    public RegistroService registroService(UsuarioRepositoryPort usuarios, HashContrasenaPort hash,
                                           BrokerPort broker, AuditoriaPort auditoria) {
        return new RegistroService(usuarios, hash, broker, auditoria);
    }

    @Bean
    public UsuarioRepositoryPort usuarioRepositoryPort(JdbcTemplate jdbc) {
        return new UsuarioJdbcRepository(jdbc);
    }

    @Bean
    public HashContrasenaPort hashContrasenaPort(PasswordEncoder encoder) {
        return new PasswordEncoderHashAdapter(encoder);
    }

    @Bean
    public AuditoriaPort auditoriaPort() {
        return new AuditoriaLogAdapter();
    }

    @Bean
    public RestClient alpacaBrokerRestClient(AuthProperties properties) {
        AuthProperties.Alpaca alpaca = properties.alpaca();
        Duration timeout = Duration.ofSeconds(alpaca.timeoutSegundos());
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(alpaca.baseUrl()).requestFactory(factory).build();
    }

    @Bean
    public BrokerPort brokerPort(@Qualifier("alpacaBrokerRestClient") RestClient client,
                                 AuthProperties properties) {
        return new AlpacaBrokerAdapter(client, properties.alpaca());
    }

    @Bean
    @ConditionalOnProperty(prefix = "accionesud.auth.reintento", name = "habilitado",
            havingValue = "true", matchIfMissing = true)
    public AltaPendienteScheduler altaPendienteScheduler(ReintentarAltasPendientesUseCase reintentar,
                                                         AuthProperties properties) {
        return new AltaPendienteScheduler(reintentar, properties.reintento().maximoPorCiclo());
    }
}