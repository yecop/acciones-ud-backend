package co.edu.udistrital.accionesud.config;

import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthDemoDataInitializer;
import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
import co.edu.udistrital.accionesud.shared.infrastructure.config.ValidadorConfiguracionDespliegue;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigPerfilesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    private ApplicationContextRunner conPerfil(String perfil) {
        return runner.withPropertyValues(
                "spring.main.banner-mode=off",
                "spring.profiles.active=" + perfil,
                "DATABASE_URL=jdbc:postgresql://db:5432/acciones_ud",
                "DATABASE_USER=acciones",
                "DATABASE_PASSWORD=clave-segura-de-prueba",
                "AUTH_JWT_SECRET=secreto-jwt-de-prueba-acciones-ud-2026-32bytes-minimo",
                "ACCIONESUD_CORS_ALLOWED_ORIGINS=https://frontend.accionesud.example");
    }

    @Test
    void devTieneDatosDemoDatasourceYDetallesDeSalud() {
        conPerfil("dev").run(context -> {
            Environment entorno = context.getEnvironment();
            assertThat(entorno.getProperty("accionesud.auth.demo.habilitado")).isEqualTo("true");
            assertThat(entorno.getProperty("accionesud.auth.demo.correo"))
                    .isEqualTo("inversionista@accionesud.local");
            assertThat(entorno.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5432/acciones_ud");
            assertThat(entorno.getProperty("spring.mail.host")).isEqualTo("localhost");
            assertThat(entorno.getProperty("management.endpoint.health.show-details"))
                    .isEqualTo("always");
        });
    }

    @Test
    void testNoTieneBaseDeDatosNiActualizadorDeCotizaciones() {
        conPerfil("test").run(context -> {
            Environment entorno = context.getEnvironment();
            assertThat(entorno.getProperty("spring.autoconfigure.exclude[0]"))
                    .contains("DataSourceAutoConfiguration");
            assertThat(entorno.getProperty("accionesud.mercados.cotizaciones.actualizador-habilitado"))
                    .isEqualTo("false");
            assertThat(entorno.getProperty("accionesud.mercados.cotizaciones.cache.tipo"))
                    .isEqualTo("memoria");
            assertThat(entorno.getProperty("management.health.redis.enabled")).isEqualTo("false");
            assertThat(entorno.getProperty("management.health.mail.enabled")).isEqualTo("false");
            assertThat(entorno.getProperty("accionesud.auth.demo.habilitado")).isEqualTo("false");
            assertThat(entorno.getProperty("accionesud.auth.jwt.secreto"))
                    .asString().hasSizeGreaterThanOrEqualTo(32);
        });
    }

    @Test
    void prodNoCargaDatosDemoYNecesitaLaUrlDeBaseExplicita() {
        conPerfil("prod").run(context -> {
            Environment entorno = context.getEnvironment();
            assertThat(entorno.getProperty("accionesud.auth.demo.habilitado")).isEqualTo("false");
            assertThat(entorno.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
            assertThat(entorno.getProperty("management.endpoint.health.show-details"))
                    .isEqualTo("never");
            assertThat(entorno.getProperty("spring.mail.host")).isEqualTo("localhost");
            assertThat(entorno.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://db:5432/acciones_ud");
            assertThat(entorno.getProperty("accionesud.auth.jwt.secreto"))
                    .asString().hasSizeGreaterThanOrEqualTo(32);
        });
    }

    @Test
    void stagingRequiereBaseYNoCargaDatosDemo() {
        conPerfil("staging")
                .withUserConfiguration(ValidadorConfiguracionDespliegue.class)
                .run(context -> {
                    Environment entorno = context.getEnvironment();
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(entorno.getProperty("accionesud.auth.demo.habilitado")).isEqualTo("false");
                    assertThat(entorno.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
                    assertThat(entorno.getProperty("management.endpoint.health.show-details"))
                            .isEqualTo("when-authorized");
                    assertThat(entorno.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://db:5432/acciones_ud");
                    assertThat(entorno.getProperty("accionesud.cors.allowed-origins"))
                            .isEqualTo("https://frontend.accionesud.example");
                });
    }

    @Test
    void laConfiguracionBaseNoContieneCredencialesDemo() {
        conPerfil("sin-perfil").run(context -> {
            Environment entorno = context.getEnvironment();
            assertThat(entorno.getProperty("accionesud.auth.demo.correo")).isNull();
            assertThat(entorno.getProperty("accionesud.auth.demo.clave")).isNull();
            assertThat(entorno.getProperty("spring.mail.host")).isEqualTo("localhost");
        });
    }

    @Test
    void elInicializadorDemoNoSeRegistraSiProduccionEstaActiva() {
        new ApplicationContextRunner()
                .withUserConfiguration(InicializadorDemo.class)
                .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
                .withBean(PasswordEncoder.class, () -> Mockito.mock(PasswordEncoder.class))
                .withBean(AuthProperties.class, AuthProperties::new)
                .withPropertyValues("spring.profiles.active=prod,dev",
                        "accionesud.auth.demo.habilitado=false")
                .run(context -> assertThat(context).doesNotHaveBean(AuthDemoDataInitializer.class));
    }

    @Test
    void elInicializadorDemoSeRegistraEnDesarrollo() {
        new ApplicationContextRunner()
                .withUserConfiguration(InicializadorDemo.class)
                .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
                .withBean(PasswordEncoder.class, () -> Mockito.mock(PasswordEncoder.class))
                .withBean(AuthProperties.class, AuthProperties::new)
                .withPropertyValues("spring.profiles.active=dev",
                        "accionesud.auth.demo.habilitado=true")
                .run(context -> assertThat(context).hasSingleBean(AuthDemoDataInitializer.class));
    }

    @Test
    void prodFallaSiFaltaElSecretoJwt() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ValidadorConfiguracionDespliegue.class)
                .withPropertyValues(
                        "spring.main.banner-mode=off",
                        "spring.profiles.active=prod",
                        "DATABASE_URL=jdbc:postgresql://db:5432/acciones_ud",
                        "DATABASE_USER=acciones",
                        "DATABASE_PASSWORD=clave-segura-de-prueba")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasRootCauseMessage("AUTH_JWT_SECRET es obligatoria y debe tener al menos 32 caracteres"));
    }

    @Import(AuthDemoDataInitializer.class)
    static class InicializadorDemo {
    }
}
