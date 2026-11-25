package co.edu.udistrital.accionesud.config;

import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthDemoDataInitializer;
import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
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
                "DATABASE_URL=",
                "DATABASE_USER=",
                "DATABASE_PASSWORD=");
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
            assertThat(entorno.getProperty("spring.datasource.url")).isEmpty();
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

    @Import(AuthDemoDataInitializer.class)
    static class InicializadorDemo {
    }
}
