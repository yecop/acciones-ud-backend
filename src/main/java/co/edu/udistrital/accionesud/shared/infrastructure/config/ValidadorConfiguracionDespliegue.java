package co.edu.udistrital.accionesud.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile({"prod", "staging"})
public class ValidadorConfiguracionDespliegue {

    public ValidadorConfiguracionDespliegue(Environment environment) {
        requerido(environment, "spring.datasource.url", "DATABASE_URL");
        requerido(environment, "spring.datasource.username", "DATABASE_USER");
        requerido(environment, "spring.datasource.password", "DATABASE_PASSWORD");

        String secreto = environment.getProperty("accionesud.auth.jwt.secreto");
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException(
                    "AUTH_JWT_SECRET es obligatoria y debe tener al menos 32 caracteres");
        }

        if (environment.matchesProfiles("staging")) {
            requerido(environment, "accionesud.cors.allowed-origins",
                    "ACCIONESUD_CORS_ALLOWED_ORIGINS");
        }
    }

    private static void requerido(Environment environment, String propiedad, String variable) {
        String valor = environment.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(variable + " es obligatoria en este perfil");
        }
    }
}
