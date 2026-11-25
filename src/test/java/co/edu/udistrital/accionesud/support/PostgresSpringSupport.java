package co.edu.udistrital.accionesud.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class PostgresSpringSupport extends PostgresIntegrationSupport {

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    protected void limpiarDatos() {
        new ResourceDatabasePopulator(new ClassPathResource("db/limpiar-datos.sql"))
                .execute(jdbc.getDataSource());
    }

    @DynamicPropertySource
    protected static void propiedadesFlyway(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.validate-on-migrate", () -> "true");
    }
}
