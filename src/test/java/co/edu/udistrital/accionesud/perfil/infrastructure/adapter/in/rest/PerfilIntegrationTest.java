package co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.support.PostgresSpringSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PerfilIntegrationTest extends PostgresSpringSupport {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("accionesud.auth.reintento.habilitado", () -> "false");
    }

    private static final String PREFERENCIAS = """
            {
              "tipoVisualizacion": "BARRAS",
              "ordenPredeterminado": "PORCENTAJE_GANANCIA",
              "ocultarActivosMenorPeso": true,
              "tipoOrdenPredeterminado": "LIMIT",
              "limiteOperacionesDiarias": 10,
              "dobleAutorizacion": true,
              "umbralOrdenGrande": "5000.00",
              "reporteMensual": true,
              "canales": ["EMAIL", "SMS"],
              "eventos": ["APERTURA_CIERRE_MERCADO", "ORDEN_EJECUTADA"]
            }""";

    @Autowired MockMvc mockMvc;

    private UUID crearInversionista(String correo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, rol_id, estado_usuario_id, nombre, apellido, correo,
                                     contrasena_hash, celular, fecha_nacimiento)
                VALUES (?, 1, 1, 'Ana', 'Perez', ?, 'hash', '+573001112233', DATE '1999-05-20')""", id, correo);
        jdbc.update("""
                INSERT INTO inversionista (usuario_id, nivel_experiencia_id, nivel_riesgo, objetivos_financieros)
                VALUES (?, 1, 'MEDIO', 'Ahorrar')""", id);
        jdbc.update("""
                INSERT INTO inversionista_interes_mercado (inversionista_id, mercado_id)
                SELECT ?, id FROM mercado WHERE codigo = 'NASDAQ'""", id);
        jdbc.update("""
                INSERT INTO inversionista_interes_sector (inversionista_id, sector_id)
                SELECT ?, id FROM sector WHERE nombre = 'Tecnología'""", id);
        return id;
    }

    private static String correoUnico() {
        return "ana-" + UUID.randomUUID() + "@example.test";
    }

    private static RequestPostProcessor inversionista(UUID id) {
        return user(id.toString()).roles("INVERSIONISTA");
    }

    private ResultActions obtenerPreferencias(UUID id) throws Exception {
        return mockMvc.perform(get("/api/v1/perfil/preferencias").with(inversionista(id)));
    }

    private ResultActions guardarPreferencias(UUID id, String cuerpo) throws Exception {
        return mockMvc.perform(put("/api/v1/perfil/preferencias").with(inversionista(id))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions modificarPerfil(UUID id, String cuerpo) throws Exception {
        return mockMvc.perform(patch("/api/v1/perfil").with(inversionista(id))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void esperarPreferenciasGuardadas(ResultActions resultado) throws Exception {
        resultado
                .andExpect(jsonPath("$.tipoVisualizacion").value("BARRAS"))
                .andExpect(jsonPath("$.ordenPredeterminado").value("PORCENTAJE_GANANCIA"))
                .andExpect(jsonPath("$.ocultarActivosMenorPeso").value(true))
                .andExpect(jsonPath("$.tipoOrdenPredeterminado").value("LIMIT"))
                .andExpect(jsonPath("$.limiteOperacionesDiarias").value(10))
                .andExpect(jsonPath("$.dobleAutorizacion").value(true))
                .andExpect(jsonPath("$.umbralOrdenGrande").value("5000.00"))
                .andExpect(jsonPath("$.reporteMensual").value(true))
                .andExpect(jsonPath("$.canales", containsInAnyOrder("EMAIL", "SMS")))
                .andExpect(jsonPath("$.eventos", containsInAnyOrder("APERTURA_CIERRE_MERCADO", "ORDEN_EJECUTADA")));
    }

    @Test
    void obtienePerfilDelUsuario() throws Exception {
        String correo = correoUnico();
        UUID id = crearInversionista(correo);

        mockMvc.perform(get("/api/v1/perfil").with(inversionista(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(id.toString()))
                .andExpect(jsonPath("$.nombres").value("Ana"))
                .andExpect(jsonPath("$.apellidos").value("Perez"))
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.nivelExperiencia").value("PRINCIPIANTE"))
                .andExpect(jsonPath("$.nivelRiesgo").value("MEDIO"))
                .andExpect(jsonPath("$.mercados[0]").value("NASDAQ"))
                .andExpect(jsonPath("$.sectores[0]").value("Tecnología"));
    }

    @Test
    void identificaAlUsuarioPorCorreoCuandoElPrincipalNoEsUuid() throws Exception {
        String correo = correoUnico();
        UUID id = crearInversionista(correo);

        mockMvc.perform(get("/api/v1/perfil").with(user(correo).roles("INVERSIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(id.toString()));
    }

    @Test
    void actualizaElPerfilParcialmenteYPersiste() throws Exception {
        UUID id = crearInversionista(correoUnico());

        modificarPerfil(id, """
                {"nombres": "Ana María", "celular": "+573009998877", "nivelRiesgo": "ALTO", "mercados": ["NYSE"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Ana María"))
                .andExpect(jsonPath("$.apellidos").value("Perez"))
                .andExpect(jsonPath("$.celular").value("+573009998877"))
                .andExpect(jsonPath("$.nivelRiesgo").value("ALTO"))
                .andExpect(jsonPath("$.mercados.length()").value(1))
                .andExpect(jsonPath("$.mercados[0]").value("NYSE"))
                .andExpect(jsonPath("$.sectores[0]").value("Tecnología"));

        assertEquals("Ana María", jdbc.queryForObject(
                "SELECT nombre FROM usuario WHERE id = ?", String.class, id));
        assertEquals("ALTO", jdbc.queryForObject(
                "SELECT nivel_riesgo FROM inversionista WHERE usuario_id = ?", String.class, id));
    }

    @Test
    void patchConListasVaciasEliminaInteresesDeMercadosYSectores() throws Exception {
        UUID id = crearInversionista(correoUnico());

        modificarPerfil(id, "{\"mercados\": [], \"sectores\": []}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mercados.length()").value(0))
                .andExpect(jsonPath("$.sectores.length()").value(0));

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM inversionista_interes_mercado WHERE inversionista_id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM inversionista_interes_sector WHERE inversionista_id = ?", Integer.class, id));
    }

    @Test
    void patchDeUsuarioSinPerfilDeInversionistaNoModificaDatosDeUsuario() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, rol_id, estado_usuario_id, nombre, apellido, correo,
                                     contrasena_hash, celular, fecha_nacimiento)
                VALUES (?, 1, 1, 'Bruno', 'Lopez', ?, 'hash', '+573001112233', DATE '1999-05-20')""",
                id, correoUnico());

        mockMvc.perform(patch("/api/v1/perfil").with(inversionista(id))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombres\":\"Cambio no válido\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERFIL_NO_ENCONTRADO"));

        assertEquals("Bruno", jdbc.queryForObject(
                "SELECT nombre FROM usuario WHERE id = ?", String.class, id));
    }

    @Test
    void patchConFormatoInvalidoDevuelveDetallePorCampo() throws Exception {
        UUID id = crearInversionista(correoUnico());

        modificarPerfil(id, "{\"celular\": \"abc\", \"mercados\": [\" \"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.datos.campos[*].campo", containsInAnyOrder("celular", "mercados[]")));
    }

    @Test
    void patchConMercadoInexistenteDevuelveValidacionFallida() throws Exception {
        UUID id = crearInversionista(correoUnico());

        modificarPerfil(id, "{\"mercados\": [\"XXX\"]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("INTERES_NO_VALIDO"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("mercado 'xxx'")));

        // La transacción se revierte: el mercado anterior sigue guardado.
        mockMvc.perform(get("/api/v1/perfil").with(inversionista(id)))
                .andExpect(jsonPath("$.mercados[0]").value("NASDAQ"));
    }

    @Test
    void preferenciasSinGuardarDevuelvenDefectoConTrailingStopDeshabilitado() throws Exception {
        UUID id = crearInversionista(correoUnico());

        obtenerPreferencias(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoVisualizacion").value("LISTA"))
                .andExpect(jsonPath("$.tipoOrdenPredeterminado").value("MARKET"))
                .andExpect(jsonPath("$.dobleAutorizacion").value(false))
                .andExpect(jsonPath("$.canales", containsInAnyOrder("EMAIL")))
                .andExpect(jsonPath("$.eventos", containsInAnyOrder(
                        "APERTURA_CIERRE_MERCADO", "RESUMEN_DIARIO_OPERACIONES", "ORDEN_EJECUTADA")))
                .andExpect(jsonPath("$.tiposOrdenDisponibles[?(@.codigo == 'TRAILING_STOP')].habilitado").value(false))
                .andExpect(jsonPath("$.tiposOrdenDisponibles[?(@.codigo == 'LIMIT')].habilitado").value(true));

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_perfil WHERE inversionista_id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_canal WHERE inversionista_id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_evento WHERE inversionista_id = ?", Integer.class, id));
    }

    @Test
    void lasPreferenciasPersistenYSeDevuelvenIgualQueSeGuardaron() throws Exception {
        UUID id = crearInversionista(correoUnico());

        esperarPreferenciasGuardadas(guardarPreferencias(id, PREFERENCIAS).andExpect(status().isOk()));
        esperarPreferenciasGuardadas(obtenerPreferencias(id).andExpect(status().isOk()));

        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_perfil WHERE inversionista_id = ?", Integer.class, id));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_canal WHERE inversionista_id = ?", Integer.class, id));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_evento WHERE inversionista_id = ?", Integer.class, id));
        assertEquals("BARRAS", jdbc.queryForObject(
                "SELECT tipo_visualizacion FROM preferencia_perfil WHERE inversionista_id = ?", String.class, id));
        assertEquals("PORCENTAJE_GANANCIA", jdbc.queryForObject(
                "SELECT orden_predeterminado FROM preferencia_perfil WHERE inversionista_id = ?", String.class, id));
        assertEquals(2, jdbc.queryForObject(
                "SELECT tipo_orden_id FROM preferencia_perfil WHERE inversionista_id = ?", Integer.class, id));
        assertEquals(10, jdbc.queryForObject(
                "SELECT limite_operaciones_diarias FROM preferencia_perfil WHERE inversionista_id = ?", Integer.class, id));
        assertEquals(true, jdbc.queryForObject(
                "SELECT doble_autorizacion FROM preferencia_perfil WHERE inversionista_id = ?", Boolean.class, id));
        assertEquals("5000.00", jdbc.queryForObject(
                "SELECT umbral_orden_grande::text FROM preferencia_perfil WHERE inversionista_id = ?", String.class, id));
        assertEquals(true, jdbc.queryForObject(
                "SELECT reporte_mensual FROM preferencia_perfil WHERE inversionista_id = ?", Boolean.class, id));
        assertEquals(Set.of("EMAIL", "SMS"), Set.copyOf(jdbc.queryForList("""
                SELECT c.nombre FROM preferencia_canal pc
                JOIN canal_notificacion c ON c.id = pc.canal_id
                WHERE pc.inversionista_id = ?""", String.class, id)));
        assertEquals(Set.of("APERTURA_CIERRE_MERCADO", "ORDEN_EJECUTADA"), Set.copyOf(jdbc.queryForList("""
                SELECT e.nombre FROM preferencia_evento pe
                JOIN evento_notificacion e ON e.id = pe.evento_id
                WHERE pe.inversionista_id = ?""", String.class, id)));
    }

    @Test
    void unSegundoPutReemplazaCanalesYEventos() throws Exception {
        UUID id = crearInversionista(correoUnico());
        guardarPreferencias(id, PREFERENCIAS).andExpect(status().isOk());

        String nuevo = PREFERENCIAS
                .replace("[\"EMAIL\", \"SMS\"]", "[\"WHATSAPP\"]")
                .replace("[\"APERTURA_CIERRE_MERCADO\", \"ORDEN_EJECUTADA\"]", "[]")
                .replace("\"limiteOperacionesDiarias\": 10", "\"limiteOperacionesDiarias\": null");
        guardarPreferencias(id, nuevo).andExpect(status().isOk());

        obtenerPreferencias(id)
                .andExpect(jsonPath("$.canales.length()").value(1))
                .andExpect(jsonPath("$.canales[0]").value("WHATSAPP"))
                .andExpect(jsonPath("$.eventos.length()").value(0))
                .andExpect(jsonPath("$.limiteOperacionesDiarias").doesNotExist());
    }

    @Test
    void reglasDeNegocioInvalidasDevuelvenValidacionFallidaPorCampo() throws Exception {
        UUID id = crearInversionista(correoUnico());
        String cuerpo = PREFERENCIAS
                .replace("\"LIMIT\"", "\"TRAILING_STOP\"")
                .replace("\"umbralOrdenGrande\": \"5000.00\"", "\"umbralOrdenGrande\": null");

        guardarPreferencias(id, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.datos.campos[?(@.campo == 'tipoOrdenPredeterminado')]").exists())
                .andExpect(jsonPath("$.datos.campos[?(@.campo == 'umbralOrdenGrande')]").exists());

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_perfil WHERE inversionista_id = ?", Integer.class, id));
    }

    @Test
    void preferenciasInvalidasNoAlteranLasPreferenciasPreviamenteGuardadas() throws Exception {
        UUID id = crearInversionista(correoUnico());
        guardarPreferencias(id, PREFERENCIAS).andExpect(status().isOk());

        String invalida = PREFERENCIAS.replace("\"limiteOperacionesDiarias\": 10",
                "\"limiteOperacionesDiarias\": 0");
        guardarPreferencias(id, invalida)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"));

        esperarPreferenciasGuardadas(obtenerPreferencias(id).andExpect(status().isOk()));
        assertEquals(10, jdbc.queryForObject(
                "SELECT limite_operaciones_diarias FROM preferencia_perfil WHERE inversionista_id = ?",
                Integer.class, id));
    }

    @Test
    void preferenciasDeUnUsuarioNoSonVisiblesParaOtro() throws Exception {
        UUID propietario = crearInversionista(correoUnico());
        UUID otroUsuario = crearInversionista(correoUnico());
        guardarPreferencias(propietario, PREFERENCIAS).andExpect(status().isOk());

        obtenerPreferencias(otroUsuario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoVisualizacion").value("LISTA"))
                .andExpect(jsonPath("$.tipoOrdenPredeterminado").value("MARKET"))
                .andExpect(jsonPath("$.canales", containsInAnyOrder("EMAIL")));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_perfil WHERE inversionista_id = ?", Integer.class, otroUsuario));
    }

    @Test
    void formatoInvalidoDevuelveValidacionFallidaPorCampo() throws Exception {
        UUID id = crearInversionista(correoUnico());
        String cuerpo = PREFERENCIAS
                .replace("\"limiteOperacionesDiarias\": 10", "\"limiteOperacionesDiarias\": 0")
                .replace("[\"EMAIL\", \"SMS\"]", "[]");

        guardarPreferencias(id, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.datos.campos[?(@.campo == 'limiteOperacionesDiarias')]").exists())
                .andExpect(jsonPath("$.datos.campos[?(@.campo == 'canales')]").exists());
    }

    @Test
    void valorDeEnumDesconocidoDevuelveValidacionFallidaPorCampo() throws Exception {
        UUID id = crearInversionista(correoUnico());

        guardarPreferencias(id, PREFERENCIAS.replace("\"BARRAS\"", "\"CUADRICULA\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.datos.campos[?(@.campo == 'tipoVisualizacion')]").exists());
    }

    @Test
    void elementosNulosEnCanalesYEventosDevuelven400SinGuardar() throws Exception {
        UUID id = crearInversionista(correoUnico());
        String canalNulo = PREFERENCIAS.replace("[\"EMAIL\", \"SMS\"]", "[\"EMAIL\", null]");
        String eventoNulo = PREFERENCIAS.replace("[\"APERTURA_CIERRE_MERCADO\", \"ORDEN_EJECUTADA\"]",
                "[\"APERTURA_CIERRE_MERCADO\", null]");

        guardarPreferencias(id, canalNulo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.detalles", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("canales"))));
        guardarPreferencias(id, eventoNulo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERROR_VALIDACION"))
                .andExpect(jsonPath("$.detalles", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("eventos"))));

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM preferencia_perfil WHERE inversionista_id = ?", Integer.class, id));
    }

    @Test
    void otroRolRecibe403() throws Exception {
        UUID id = crearInversionista(correoUnico());

        mockMvc.perform(get("/api/v1/perfil/preferencias").with(user(id.toString()).roles("COMISIONISTA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        mockMvc.perform(put("/api/v1/perfil/preferencias").with(user(id.toString()).roles("COMISIONISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(PREFERENCIAS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        mockMvc.perform(patch("/api/v1/perfil").with(user(id.toString()).roles("COMISIONISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombres\":\"No autorizado\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void sinAutenticacionRecibe401() throws Exception {
        mockMvc.perform(get("/api/v1/perfil"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        mockMvc.perform(patch("/api/v1/perfil")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombres\":\"No autenticado\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        mockMvc.perform(put("/api/v1/perfil/preferencias")
                        .contentType(MediaType.APPLICATION_JSON).content(PREFERENCIAS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
