package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence.InMemorySaldoRepository;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.port.out.CotizacionCachePort;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.comisionistas.AccesoComisionistaEnMemoriaAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrdenesControllerIntegrationTest {

    private static final String URL = "/api/v1/ordenes/simulacion";

    @Autowired MockMvc mvc;
    @Autowired CotizacionCachePort cotizaciones;
    @Autowired InMemorySaldoRepository saldos;
    @Autowired AccesoComisionistaEnMemoriaAdapter comisionistas;

    private UUID inversionista;

    @BeforeEach
    void preparar() {
        inversionista = UUID.randomUUID();
        if (cotizaciones instanceof co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache.InMemoryCotizacionCacheAdapter memoria) {
            memoria.limpiar();
        }
        Instant ahora = Instant.now();
        cotizaciones.guardar(Cotizacion.de("AAPL", new BigDecimal("175.00"), new BigDecimal("174.99"),
                new BigDecimal("175.01"), 1000, ahora, "PRUEBA"));
        saldos.guardar(inversionista.toString(), new Saldo(new BigDecimal("1000.00"), new BigDecimal("25.00")));
        comisionistas.finalizar(inversionista);
    }

    @Test
    void simulaCompraValidaComisionYFondosSinModificarSaldo() throws Exception {
        Saldo antes = saldos.buscarPorUsuario(inversionista.toString()).orElseThrow();

        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"COMPRA","cantidad":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.precioEstimado").value("175.0000"))
                .andExpect(jsonPath("$.totalTransaccion").value("1750.00"))
                .andExpect(jsonPath("$.comision.porcentaje").value("2.00"))
                .andExpect(jsonPath("$.comision.valor").value("35.00"))
                .andExpect(jsonPath("$.comision.valorPlataforma").value("35.00"))
                .andExpect(jsonPath("$.comision.valorComisionista").value("0.00"))
                .andExpect(jsonPath("$.totalADescontar").value("1785.00"))
                .andExpect(jsonPath("$.fondosSuficientes").value(false))
                .andExpect(jsonPath("$.actualizadoEn").exists())
                .andExpect(jsonPath("$.mercadoAbierto").exists());

        Saldo despues = saldos.buscarPorUsuario(inversionista.toString()).orElseThrow();
        assertThat(despues).isEqualTo(antes);
    }

    @Test
    void simulaVentaYDivideComisionCuandoElInversionistaTieneComisionista() throws Exception {
        UUID comisionista = UUID.randomUUID();
        comisionistas.asociar(inversionista, comisionista);

        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"VENTA","cantidad":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comision.valor").value("35.00"))
                .andExpect(jsonPath("$.comision.valorPlataforma").value("21.00"))
                .andExpect(jsonPath("$.comision.valorComisionista").value("14.00"))
                .andExpect(jsonPath("$.totalADescontar").value("1715.00"))
                .andExpect(jsonPath("$.fondosSuficientes").value(true));
    }

    @Test
    void validaLosCamposDeEntrada() throws Exception {
        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"COMPRA","cantidad":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }
}
