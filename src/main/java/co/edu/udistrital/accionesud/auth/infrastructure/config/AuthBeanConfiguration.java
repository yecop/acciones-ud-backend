package co.edu.udistrital.accionesud.auth.infrastructure.config;

import co.edu.udistrital.accionesud.auth.application.dto.PoliticaAutenticacion;
import co.edu.udistrital.accionesud.auth.application.port.out.AuditoriaAuthPort;
import co.edu.udistrital.accionesud.auth.application.port.out.GeneradorCodigoMfaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.GeneradorTokenPort;
import co.edu.udistrital.accionesud.auth.application.port.out.HasheadorPort;
import co.edu.udistrital.accionesud.auth.application.port.out.MfaRepositoryPort;
import co.edu.udistrital.accionesud.auth.application.port.out.NotificadorMfaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.RefreshTokenRepositoryPort;
import co.edu.udistrital.accionesud.auth.application.port.out.RelojPort;
import co.edu.udistrital.accionesud.auth.application.port.out.TokenJwtPort;
import co.edu.udistrital.accionesud.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.udistrital.accionesud.auth.application.port.out.VerificadorContrasenaPort;
import co.edu.udistrital.accionesud.auth.application.service.ServicioAutenticacion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.UUID;

@Configuration
@Profile("!test")
public class AuthBeanConfiguration {

    @Bean
    public ServicioAutenticacion servicioAutenticacion(
            UsuarioRepositoryPort usuarios,
            MfaRepositoryPort desafios,
            RefreshTokenRepositoryPort refreshTokens,
            NotificadorMfaPort notificador,
            GeneradorCodigoMfaPort generadorCodigo,
            GeneradorTokenPort generadorToken,
            HasheadorPort hasheador,
            VerificadorContrasenaPort verificadorContrasena,
            TokenJwtPort tokenJwt,
            RelojPort reloj,
            AuditoriaAuthPort auditoria,
            AuthProperties properties,
            PasswordEncoder passwordEncoder) {
        PoliticaAutenticacion politica = new PoliticaAutenticacion(
                Duration.ofSeconds(properties.getMfa().getExpiracionSegundos()),
                properties.getMfa().getMaxIntentos(),
                Duration.ofSeconds(properties.getMfa().getBloqueoSegundos()),
                Duration.ofSeconds(properties.getMfa().getReenvioEsperaSegundos()),
                properties.getMfa().getMaxReenvios(),
                Duration.ofSeconds(properties.getJwt().getRefreshTtlSegundos()),
                properties.getLogin().getMaxFallos(),
                Duration.ofSeconds(properties.getLogin().getVentanaSegundos()));
        String contrasenaFicticiaHash = passwordEncoder.encode(UUID.randomUUID().toString());

        return new ServicioAutenticacion(
                usuarios, desafios, refreshTokens, notificador, generadorCodigo, generadorToken,
                hasheador, verificadorContrasena, tokenJwt, reloj, auditoria, politica,
                contrasenaFicticiaHash);
    }
}
