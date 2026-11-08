package co.edu.udistrital.accionesud.cuenta.application.service;

import co.edu.udistrital.accionesud.cuenta.application.dto.PosicionValorada;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResumenPortafolio;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PortafolioRepositoryPort;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PrecioCachePort;
import co.edu.udistrital.accionesud.cuenta.domain.model.Portafolio;
import co.edu.udistrital.accionesud.cuenta.domain.model.Posicion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pruebas del caso de uso de portafolio: valoracion de posiciones con precios
 * de la cache y calculo del resumen, incluyendo el escenario de usuario nuevo
 * (portafolio vacio) exigido por SCRUM-25.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PortafolioService")
class PortafolioServiceTest {

    @Mock
    private PortafolioRepositoryPort portafolioRepository;

    @Mock
    private PrecioCachePort precioCache;

    private PortafolioService service;

    @BeforeEach
    void setUp() {
        service = new PortafolioService(portafolioRepository, precioCache);
    }

    @Test
    @DisplayName("usuario nuevo: resumen en ceros y 0 posiciones")
    void usuarioNuevoResumenVacio() {
        when(portafolioRepository.buscarPorUsuario("nuevo")).thenReturn(Optional.empty());

        ResumenPortafolio resumen = service.consultarResumen("nuevo");

        assertEquals(new BigDecimal("0.00"), resumen.getValorTotal());
        assertEquals(new BigDecimal("0.00"), resumen.getCostoTotal());
        assertEquals(new BigDecimal("0.00"), resumen.getGananciaPerdidaTotal());
        assertEquals(new BigDecimal("0.00"), resumen.getPorcentajeGananciaPerdidaTotal());
        assertEquals(0, resumen.getNumeroPosiciones());
    }

    @Test
    @DisplayName("usuario nuevo: lista de posiciones vacia")
    void usuarioNuevoPosicionesVacias() {
        when(portafolioRepository.buscarPorUsuario("nuevo")).thenReturn(Optional.empty());
        assertTrue(service.consultarPosiciones("nuevo").isEmpty());
    }

    @Test
    @DisplayName("posiciones valoradas con precio de la cache y P&L correcto")
    void posicionesValoradas() {
        Portafolio portafolio = new Portafolio("u1", Arrays.asList(
                new Posicion("AAPL", new BigDecimal("10"), new BigDecimal("100.00")),
                new Posicion("MSFT", new BigDecimal("5"), new BigDecimal("200.00"))));
        when(portafolioRepository.buscarPorUsuario("u1")).thenReturn(Optional.of(portafolio));
        when(precioCache.obtenerPrecioActual("AAPL")).thenReturn(new BigDecimal("150.00"));
        when(precioCache.obtenerPrecioActual("MSFT")).thenReturn(new BigDecimal("180.00"));

        List<PosicionValorada> posiciones = service.consultarPosiciones("u1");

        assertEquals(2, posiciones.size());

        PosicionValorada aapl = posiciones.get(0);
        assertEquals("AAPL", aapl.getActivo());
        assertEquals(new BigDecimal("1500.00"), aapl.getValorActual());   // 10*150
        assertEquals(new BigDecimal("500.00"), aapl.getGananciaPerdida()); // 1500-1000
        assertEquals(new BigDecimal("50.00"), aapl.getPorcentajeGananciaPerdida());

        PosicionValorada msft = posiciones.get(1);
        assertEquals("MSFT", msft.getActivo());
        assertEquals(new BigDecimal("900.00"), msft.getValorActual());    // 5*180
        assertEquals(new BigDecimal("-100.00"), msft.getGananciaPerdida()); // 900-1000
        assertEquals(new BigDecimal("-10.00"), msft.getPorcentajeGananciaPerdida());
    }

    @Test
    @DisplayName("resumen agrega valor, costo, P&L y porcentaje total")
    void resumenAgregado() {
        Portafolio portafolio = new Portafolio("u1", Arrays.asList(
                new Posicion("AAPL", new BigDecimal("10"), new BigDecimal("100.00")),
                new Posicion("MSFT", new BigDecimal("5"), new BigDecimal("200.00"))));
        when(portafolioRepository.buscarPorUsuario("u1")).thenReturn(Optional.of(portafolio));
        when(precioCache.obtenerPrecioActual("AAPL")).thenReturn(new BigDecimal("150.00"));
        when(precioCache.obtenerPrecioActual("MSFT")).thenReturn(new BigDecimal("180.00"));

        ResumenPortafolio resumen = service.consultarResumen("u1");

        // valor: 1500 + 900 = 2400.00 ; costo: 1000 + 1000 = 2000.00
        assertEquals(new BigDecimal("2400.00"), resumen.getValorTotal());
        assertEquals(new BigDecimal("2000.00"), resumen.getCostoTotal());
        assertEquals(new BigDecimal("400.00"), resumen.getGananciaPerdidaTotal());
        // 400 / 2000 * 100 = 20.00%
        assertEquals(new BigDecimal("20.00"), resumen.getPorcentajeGananciaPerdidaTotal());
        assertEquals(2, resumen.getNumeroPosiciones());
    }

    @Test
    @DisplayName("porcentaje total es 0.00 cuando el costo total es cero")
    void porcentajeTotalCostoCero() {
        Portafolio portafolio = new Portafolio("u1", Arrays.asList(
                new Posicion("AAPL", new BigDecimal("10"), new BigDecimal("0.00"))));
        when(portafolioRepository.buscarPorUsuario("u1")).thenReturn(Optional.of(portafolio));
        lenient().when(precioCache.obtenerPrecioActual("AAPL")).thenReturn(new BigDecimal("150.00"));

        ResumenPortafolio resumen = service.consultarResumen("u1");

        assertEquals(new BigDecimal("0.00"), resumen.getPorcentajeGananciaPerdidaTotal());
    }
}
