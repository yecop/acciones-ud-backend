package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.comisionistas.AccesoComisionistaEnMemoriaAdapter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Contrato REST de SCRUM-46 (y el saldo de SCRUM-25) sobre el almacenamiento en memoria (perfil test). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CuentaControllerIntegrationTest {

    private static final String URL = "/api/v1/cuenta";

    @Autowired MockMvc mvc;
    @Autowired AccesoComisionistaEnMemoriaAdapter comisionistas;

    private UUID ana;

    @BeforeEach
    void preparar() {
        ana = UUID.randomUUID();
    }

    private static RequestPostProcessor inversionista(UUID id) {
        return user(id.toString()).roles("INVERSIONISTA");
    }

    private static RequestPostProcessor comisionista(UUID id) {
        return user(id.toString()).roles("COMISIONISTA");
    }

    private static MockHttpServletRequestBuilder operar(String ruta, String clave, String monto) {
        MockHttpServletRequestBuilder solicitud = post(URL + ruta).contentType(MediaType.APPLICATION_JSON)
                .content("{\"monto\": " + monto + "}");
        return clave == null ? solicitud : solicitud.header("Idempotency-Key", clave);
    }

    @Test
    void elSaldoEsDelUsuarioDelTokenYEmpiezaEnElSaldoInicial() throws Exception {
        mvc.perform(get(URL + "/saldo").with(inversionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value("100000.00"))
                .andExpect(jsonPath("$.reservado").value("0.00"))
                .andExpect(jsonPath("$.total").value("100000.00"));
    }

    @Test
    void depositarResponde201ConElMovimientoYElSaldo() throws Exception {
        mvc.perform(operar("/depositos", "dep-1", "\"500.00\"").with(inversionista(ana)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimiento.tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$.movimiento.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.movimiento.monto").value("500.00"))
                .andExpect(jsonPath("$.movimiento.moneda").value("USD"))
                .andExpect(jsonPath("$.movimiento.traceId").isNotEmpty())
                .andExpect(jsonPath("$.saldo.disponible").value("100500.00"));

        // La misma clave no vuelve a depositar.
        mvc.perform(operar("/depositos", "dep-1", "500").with(inversionista(ana)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.saldo.disponible").value("100500.00"));
    }

    @Test
    void retirarMasDelSaldoResponde422SinTocarElSaldo() throws Exception {
        mvc.perform(operar("/retiros", "ret-1", "100000.01").with(inversionista(ana)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FONDOS_INSUFICIENTES"));

        mvc.perform(get(URL + "/saldo").with(inversionista(ana)))
                .andExpect(jsonPath("$.disponible").value("100000.00"));
        mvc.perform(get(URL + "/movimientos").param("tipo", "RETIRO").with(inversionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].estado").value("FALLIDO"));
    }

    @Test
    void elHistorialEsPaginadoYMasRecientePrimero() throws Exception {
        mvc.perform(operar("/depositos", "a", "1").with(inversionista(ana))).andExpect(status().isCreated());
        mvc.perform(operar("/retiros", "b", "2").with(inversionista(ana))).andExpect(status().isCreated());

        mvc.perform(get(URL + "/movimientos").param("tamano", "2").with(inversionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3)) // saldo inicial + depósito + retiro
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.tamano").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(2));
    }

    @Test
    void validaMontoClaveYPaginacion() throws Exception {
        mvc.perform(operar("/depositos", null, "10").with(inversionista(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
        mvc.perform(operar("/depositos", "k", "0").with(inversionista(ana)))
                .andExpect(status().isBadRequest());
        mvc.perform(operar("/depositos", "k", "\"abc\"").with(inversionista(ana)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/movimientos").param("tamano", "101").with(inversionista(ana)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laMismaClaveConOtroMontoEsUnConflicto() throws Exception {
        mvc.perform(operar("/depositos", "dup", "10").with(inversionista(ana))).andExpect(status().isCreated());

        mvc.perform(operar("/depositos", "dup", "11").with(inversionista(ana)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_IDEMPOTENCIA"));
    }

    @Test
    void soloElTitularOperaLaCuenta() throws Exception {
        mvc.perform(operar("/depositos", "k", "10")).andExpect(status().isUnauthorized());
        mvc.perform(operar("/depositos", "k", "10").with(comisionista(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(operar("/retiros", "k", "10").with(user(UUID.randomUUID().toString()).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL + "/movimientos").param("inversionistaId", UUID.randomUUID().toString())
                        .with(inversionista(ana)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unComisionistaSoloVeElHistorialDeUnClienteConRelacionVigente() throws Exception {
        UUID carlos = UUID.randomUUID();
        mvc.perform(get(URL + "/movimientos").with(comisionista(carlos)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/movimientos").param("inversionistaId", ana.toString()).with(comisionista(carlos)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        comisionistas.asociar(ana, carlos);
        mvc.perform(get(URL + "/movimientos").param("inversionistaId", ana.toString()).with(comisionista(carlos)))
                .andExpect(status().isOk());
    }
}
