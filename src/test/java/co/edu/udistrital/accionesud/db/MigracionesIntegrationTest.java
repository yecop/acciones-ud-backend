package co.edu.udistrital.accionesud.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class MigracionesIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcTemplate jdbc;

    @BeforeAll
    static void migrar() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    private int contar(String tabla) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
        return total == null ? 0 : total;
    }

    @Test
    void catalogosYSemillasCompletos() {
        assertEquals(5, contar("rol"));
        assertEquals(3, contar("estado_usuario"));
        assertEquals(2, contar("nivel_experiencia"));
        assertEquals(3, contar("canal_notificacion"));
        assertEquals(6, contar("sector"));
        assertEquals(4, contar("tipo_orden"));
        assertEquals(10, contar("estado_orden"));
        assertEquals(5, contar("mercado"));
        assertEquals(25, contar("accion"));
        assertEquals(15, jdbc.queryForObject(
                "SELECT count(*) FROM accion WHERE mercado_id IN (3, 4, 5) "
                        + "AND capitalizacion_referencia IS NOT NULL", Integer.class));
        assertEquals(3, contar("parametro_sistema"));
    }

    @Test
    void distribucionPorMercadoEsLaEsperada() {
        List<Integer> porMercado = jdbc.queryForList(
                "SELECT count(*) FROM accion GROUP BY mercado_id ORDER BY mercado_id", Integer.class);
        assertEquals(List.of(3, 7, 5, 5, 5), porMercado);
    }

    @Test
    void semillasSonIdempotentes() throws IOException {
        for (String script : List.of(
                "db/migration/V6__seed_catalogos.sql",
                "db/migration/V7__seed_mercados_acciones.sql",
                "db/migration/V8__capitalizacion_referencia_internacional.sql")) {
            jdbc.execute(new ClassPathResource(script).getContentAsString(StandardCharsets.UTF_8));
        }
        assertEquals(10, contar("estado_orden"));
        assertEquals(5, contar("mercado"));
        assertEquals(25, contar("accion"));
        assertEquals(15, jdbc.queryForObject(
                "SELECT count(*) FROM accion WHERE mercado_id IN (3, 4, 5) "
                        + "AND capitalizacion_referencia IS NOT NULL", Integer.class));
    }

    @Test
    void restriccionesBloqueanDatosInvalidos() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO accion (ticker, nombre_empresa, sector_id, mercado_id) "
                        + "VALUES ('ZZZ', 'Prueba', 999, 1)"));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO mercado (id, codigo, nombre, zona_horaria, hora_apertura, hora_cierre) "
                        + "VALUES (99, 'XXX', 'Invalido', 'UTC', '16:00', '09:00')"));
    }

    @Test
    void historialEsInmutableYSePuedenRegistrarEjecuciones() {
        UUID usuarioId = UUID.randomUUID();
        UUID cuentaId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();
        String sufijo = UUID.randomUUID().toString();

        jdbc.update("INSERT INTO usuario (id, rol_id, estado_usuario_id, nombre, correo, contrasena_hash) "
                + "VALUES (?, 1, 1, 'Prueba', ?, 'hash')", usuarioId, sufijo + "@example.test");
        jdbc.update("INSERT INTO inversionista (usuario_id, nivel_experiencia_id) VALUES (?, 1)", usuarioId);
        jdbc.update("INSERT INTO cuenta_corretaje (id, inversionista_id) VALUES (?, ?)", cuentaId, usuarioId);
        Integer accionId = jdbc.queryForObject("SELECT id FROM accion WHERE ticker = 'AAPL'", Integer.class);
        jdbc.update("INSERT INTO orden (id, cuenta_id, accion_id, tipo_orden_id, estado_orden_id, "
                        + "creada_por_id, validada_por_id, validada_en, lado, cantidad, trace_id) "
                        + "VALUES (?, ?, ?, 1, 1, ?, ?, now(), 'COMPRA', 2, ?)",
                ordenId, cuentaId, accionId, usuarioId, usuarioId, sufijo);
        jdbc.update("INSERT INTO orden_historial_estado (orden_id, estado_orden_id, cambiado_por_id, motivo, "
                        + "precio_referencia, precio_bid, precio_ask, precio_observado_en, trace_id) "
                        + "VALUES (?, 1, ?, 'Creación', 175.60, 175.50, 175.70, now(), ?)",
                ordenId, usuarioId, sufijo);
        jdbc.update("INSERT INTO orden_ejecucion (orden_id, id_ejecucion_externa, cantidad_ejecutada, "
                        + "precio_ejecucion, ejecutada_en, trace_id) VALUES (?, ?, 1, 175.60, now(), ?)",
                ordenId, "exec-" + sufijo, sufijo);

        assertEquals(1, contar("orden_historial_estado"));
        assertEquals(1, contar("orden_ejecucion"));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE orden_historial_estado SET motivo = 'alterado' WHERE orden_id = ?", ordenId));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "DELETE FROM orden_historial_estado WHERE orden_id = ?", ordenId));
        assertThrows(DataAccessException.class, () -> jdbc.execute("TRUNCATE orden_historial_estado"));
        assertEquals(1, contar("orden_historial_estado"));
    }
}
