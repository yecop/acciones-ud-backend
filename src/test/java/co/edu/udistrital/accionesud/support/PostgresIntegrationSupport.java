package co.edu.udistrital.accionesud.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationSupport {

    protected static PostgreSQLContainer<?> contendor() {
        return Contenedor.INSTANCIA;
    }

    @DynamicPropertySource
    protected static void propiedadesPostgres(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = contendor();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final class Contenedor {

        private static final PostgreSQLContainer<?> INSTANCIA = crear();

        private static PostgreSQLContainer<?> crear() {
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("acciones_ud_test")
                    .withUsername("acciones_test")
                    .withPassword("acciones_test");
            postgres.start();
            Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
            return postgres;
        }
    }
}
