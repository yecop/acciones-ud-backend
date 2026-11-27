package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.application.port.out.CuentaFondosPort;
import co.edu.udistrital.accionesud.cuenta.domain.model.EstadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import co.edu.udistrital.accionesud.cuenta.domain.model.TipoMovimiento;
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
    @Autowired CuentaFondosPort fondos;
    @Autowired AccesoComisionistaEnMemoriaAdapter comisionistas;

    private UUID inversionista;
    private UUID cuentaId;

    @BeforeEach
    void preparar() {
        inversionista = UUID.randomUUID();
        if (cotizaciones instanceof co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cache.InMemoryCotizacionCacheAdapter memoria) {
            memoria.limpiar();
        }
        Instant ahora = Instant.now();
        cuentaId = fondos.cuentaDe(inversionista).orElseThrow();
        fondos.aplicarSaldoInicial(new MovimientoFondos(UUID.randomUUID(), cuentaId, TipoMovimiento.DEPOSITO,
                EstadoMovimiento.COMPLETADO, new BigDecimal("1000.00"), "USD", ahora, ahora, "trace-test"),
                "saldo-prueba-" + inversionista);
        cotizaciones.guardar(Cotizacion.de("AAPL", new BigDecimal("175.00"), new BigDecimal("174.99"),
                new BigDecimal("175.01"), 1000, ahora, "PRUEBA"));
        comisionistas.finalizar(inversionista);
    }

    @Test
    void simulaCompraValidaComisionYFondosSinModificarSaldo() throws Exception {
        Saldo antes = fondos.saldo(cuentaId);

        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"COMPRA","tipo":"MARKET","cantidad":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.tipo").value("MARKET"))
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

        Saldo despues = fondos.saldo(cuentaId);
        assertThat(despues).isEqualTo(antes);
    }

    @Test
    void simulaVentaYDivideComisionCuandoElInversionistaTieneComisionista() throws Exception {
        UUID comisionista = UUID.randomUUID();
        comisionistas.asociar(inversionista, comisionista);

        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"VENTA","tipo":"MARKET","cantidad":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comision.valor").value("35.00"))
                .andExpect(jsonPath("$.comision.valorPlataforma").value("21.00"))
                .andExpect(jsonPath("$.comision.valorComisionista").value("14.00"))
                .andExpect(jsonPath("$.totalADescontar").value("1715.00"))
                .andExpect(jsonPath("$.fondosSuficientes").value(false));
    }

    @Test
    void limitUsaPrecioLimiteYRechazaSiFalta() throws Exception {
        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"COMPRA","tipo":"LIMIT","cantidad":2,
                                 "precioLimite":"100.00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("LIMIT"))
                .andExpect(jsonPath("$.precioEstimado").value("100.0000"))
                .andExpect(jsonPath("$.totalTransaccion").value("200.00"));

        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"COMPRA","tipo":"LIMIT","cantidad":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void validaLosCamposDeEntrada() throws Exception {
        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"COMPRA","tipo":"MARKET","cantidad":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void stopLossRequierePrecioStop() throws Exception {
        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"VENTA","tipo":"STOP_LOSS","cantidad":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));

        mvc.perform(post(URL).with(user(inversionista.toString()).roles("INVERSIONISTA"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","lado":"COMPRA","tipo":"TAKE_PROFIT","cantidad":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }
}
