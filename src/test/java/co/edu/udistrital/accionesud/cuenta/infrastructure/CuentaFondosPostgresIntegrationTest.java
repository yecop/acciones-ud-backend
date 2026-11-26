package co.edu.udistrital.accionesud.cuenta.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.udistrital.accionesud.cuenta.application.port.out.CuentaFondosPort;
import co.edu.udistrital.accionesud.cuenta.application.service.FondosService;
import co.edu.udistrital.accionesud.cuenta.domain.exception.ConflictoIdempotenciaException;
import co.edu.udistrital.accionesud.cuenta.domain.exception.FondosInsuficientesException;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence.CuentaFondosJdbcAdapter;
import co.edu.udistrital.accionesud.support.PostgresSpringSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SCRUM-46 contra PostgreSQL real (V3 y V14): el saldo y los movimientos se persisten, un retiro
 * sin fondos no toca la cuenta, cada movimiento guarda su traceId y las operaciones concurrentes
 * no dejan saldos incorrectos. Está en el guard del CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CuentaFondosPostgresIntegrationTest extends PostgresSpringSupport {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("accionesud.auth.reintento.habilitado", () -> "false");
        registry.add("accionesud.auth.demo.habilitado", () -> "false");
        registry.add("accionesud.notificaciones.reintento.habilitado", () -> "false");
        registry.add("accionesud.notificaciones.canales.email", () -> "simulado");
        registry.add("accionesud.mercados.cotizaciones.actualizador-habilitado", () -> "false");
        registry.add("accionesud.mercados.cotizaciones.cache.tipo", () -> "memoria");
        registry.add("management.health.redis.enabled", () -> "false");
    }

    private static final String TRACE = "5f0c6f7e-2b1a-4c3d-9e8f-0a1b2c3d4e5f";

    @Autowired MockMvc mvc;
    @Autowired FondosService servicio;
    @Autowired CuentaFondosPort fondos;

    private UUID ana;
    private UUID cuenta;

    @BeforeEach
    void preparar() {
        ana = UUID.randomUUID();
        cuenta = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, rol_id, estado_usuario_id, nombre, correo, contrasena_hash)
                VALUES (?, 1, 1, 'Ana', ?, 'hash')""", ana, "ana-" + ana + "@example.test");
        jdbc.update("INSERT INTO inversionista (usuario_id, nivel_experiencia_id) VALUES (?, 1)", ana);
        jdbc.update("INSERT INTO cuenta_corretaje (id, inversionista_id) VALUES (?, ?)", cuenta, ana);
    }

    private BigDecimal disponible() {
        return jdbc.queryForObject("SELECT saldo_disponible FROM cuenta_corretaje WHERE id = ?",
                BigDecimal.class, cuenta);
    }

    private int movimientos(String tipo, String estado) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM movimiento_fondos WHERE cuenta_id = ? AND tipo = ? AND estado = ?""",
                Integer.class, cuenta, tipo, estado);
        return n == null ? 0 : n;
    }

    @Test
    void usaElAdaptadorJdbc() {
        assertThat(fondos).isInstanceOf(CuentaFondosJdbcAdapter.class);
    }

    @Test
    void elSaldoInicialSePersisteUnaSolaVezComoDeposito() throws Exception {
        mvc.perform(get("/api/v1/cuenta/saldo").with(user(ana.toString()).roles("INVERSIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value("100000.00"));
        mvc.perform(get("/api/v1/cuenta/saldo").with(user(ana.toString()).roles("INVERSIONISTA")))
                .andExpect(jsonPath("$.disponible").value("100000.00"));

        assertThat(disponible()).isEqualByComparingTo("100000.00");
        assertThat(movimientos("DEPOSITO", "COMPLETADO")).isEqualTo(1);
    }

    @Test
    void unUsuarioSinCuentaRecibe404() throws Exception {
        mvc.perform(get("/api/v1/cuenta/saldo").with(user(UUID.randomUUID().toString()).roles("COMISIONISTA")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void cadaMovimientoGuardaMontoTipoEstadoYTraceIdDeLaSolicitud() throws Exception {
        mvc.perform(post("/api/v1/cuenta/depositos").with(user(ana.toString()).roles("INVERSIONISTA"))
                        .header("X-Trace-Id", TRACE)
                        .header("Idempotency-Key", "dep-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\": \"250.75\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimiento.traceId").value(TRACE));

        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT tipo, estado, monto, moneda, trace_id, clave_idempotencia, completado_en
                FROM movimiento_fondos WHERE cuenta_id = ? AND clave_idempotencia LIKE ?""", cuenta, "%:dep-1");
        assertThat(fila).containsEntry("tipo", "DEPOSITO").containsEntry("estado", "COMPLETADO")
                .containsEntry("moneda", "USD").containsEntry("trace_id", TRACE);
        assertThat((BigDecimal) fila.get("monto")).isEqualByComparingTo("250.75");
        assertThat(fila.get("completado_en")).isNotNull();
        assertThat(disponible()).isEqualByComparingTo("100250.75");
    }

    @Test
    void unRetiroMayorAlSaldoNoModificaLaCuentaYQuedaComoFallido() {
        servicio.consultarSaldo(ana.toString());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> servicio.retirar(ana, new BigDecimal("100000.01"), "ret-1"))
                .isInstanceOf(FondosInsuficientesException.class);

        assertThat(disponible()).isEqualByComparingTo("100000.00");
        assertThat(movimientos("RETIRO", "FALLIDO")).isEqualTo(1);
        assertThat(movimientos("RETIRO", "COMPLETADO")).isZero();
    }

    @Test
    void retirosConcurrentesNuncaDejanElSaldoNegativoNiIncorrecto() throws Exception {
        servicio.consultarSaldo(ana.toString()); // 100000.00
        List<Callable<Boolean>> tareas = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String clave = "ret-" + i;
            tareas.add(() -> {
                try {
                    servicio.retirar(ana, new BigDecimal("7000.00"), clave);
                    return true;
                } catch (FondosInsuficientesException e) {
                    return false;
                }
            });
        }
        long exitosos = enParalelo(tareas).stream().filter(Boolean::booleanValue).count();

        // 100000 / 7000 = 14 retiros caben; sobran 2000.00.
        assertThat(exitosos).isEqualTo(14);
        assertThat(disponible()).isEqualByComparingTo("2000.00");
        assertThat(movimientos("RETIRO", "COMPLETADO")).isEqualTo(14);
        assertThat(movimientos("RETIRO", "FALLIDO")).isEqualTo(16);
    }

    @Test
    void depositosYRetirosConcurrentesCuadranConElHistorial() throws Exception {
        servicio.consultarSaldo(ana.toString());
        List<Callable<Boolean>> tareas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String clave = "op-" + i;
            boolean deposito = i % 2 == 0;
            tareas.add(() -> {
                if (deposito) {
                    servicio.depositar(ana, new BigDecimal("100.10"), clave);
                } else {
                    servicio.retirar(ana, new BigDecimal("50.05"), clave);
                }
                return true;
            });
        }
        enParalelo(tareas);

        BigDecimal esperado = new BigDecimal("100000.00")
                .add(new BigDecimal("100.10").multiply(BigDecimal.TEN))
                .subtract(new BigDecimal("50.05").multiply(BigDecimal.TEN));
        assertThat(disponible()).isEqualByComparingTo(esperado);
        BigDecimal segunHistorial = jdbc.queryForObject("""
                SELECT sum(CASE WHEN tipo = 'DEPOSITO' THEN monto ELSE -monto END) FROM movimiento_fondos
                WHERE cuenta_id = ? AND estado = 'COMPLETADO'""", BigDecimal.class, cuenta);
        assertThat(segunHistorial).isEqualByComparingTo(esperado);
    }

    @Test
    void laMismaClaveEnviadaEnParaleloSoloSeAplicaUnaVez() throws Exception {
        servicio.consultarSaldo(ana.toString());
        List<Callable<Boolean>> tareas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tareas.add(() -> {
                try {
                    return !servicio.depositar(ana, new BigDecimal("500.00"), "misma").repetido();
                } catch (ConflictoIdempotenciaException e) {
                    return false;
                }
            });
        }
        long aplicados = enParalelo(tareas).stream().filter(Boolean::booleanValue).count();

        assertThat(aplicados).isEqualTo(1);
        assertThat(disponible()).isEqualByComparingTo("100500.00");
        assertThat(movimientos("DEPOSITO", "COMPLETADO")).isEqualTo(2); // saldo inicial + este
    }

    /** Lanza todas las tareas a la vez y espera sus resultados (propaga el primer error). */
    private static List<Boolean> enParalelo(List<Callable<Boolean>> tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futuros = new ArrayList<>();
            for (Callable<Boolean> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<Boolean> resultados = new ArrayList<>();
            for (Future<Boolean> f : futuros) {
                resultados.add(f.get());
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }
}
