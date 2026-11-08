package co.edu.udistrital.accionesud.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de la agregacion del portafolio: valor total, costo total
 * y ganancia/perdida total (SCRUM-25).
 */
@DisplayName("Portafolio - agregacion financiera")
class PortafolioTest {

    private static Function<String, BigDecimal> precios(Map<String, String> tabla) {
        return activo -> new BigDecimal(tabla.get(activo));
    }

    @Test
    @DisplayName("portafolio vacio: valor, costo y P&L en 0.00")
    void portafolioVacio() {
        Portafolio p = Portafolio.vacio("u1");
        assertTrue(p.estaVacio());
        assertEquals(new BigDecimal("0.00"), p.costoTotal());
        assertEquals(new BigDecimal("0.00"), p.valorTotal(a -> BigDecimal.TEN));
        assertEquals(new BigDecimal("0.00"), p.gananciaPerdidaTotal(a -> BigDecimal.TEN));
    }

    @Test
    @DisplayName("valor total suma el valor actual de todas las posiciones")
    void valorTotalMultiplesPosiciones() {
        Portafolio p = new Portafolio("u1", Arrays.asList(
                new Posicion("AAPL", new BigDecimal("10"), new BigDecimal("100.00")),
                new Posicion("MSFT", new BigDecimal("5"), new BigDecimal("200.00"))));

        Function<String, BigDecimal> precios = precios(Map.of(
                "AAPL", "150.00",   // 10 * 150 = 1500.00
                "MSFT", "220.00")); // 5 * 220 = 1100.00

        assertEquals(new BigDecimal("2600.00"), p.valorTotal(precios));
    }

    @Test
    @DisplayName("costo total suma el costo base de todas las posiciones")
    void costoTotal() {
        Portafolio p = new Portafolio("u1", Arrays.asList(
                new Posicion("AAPL", new BigDecimal("10"), new BigDecimal("100.00")),
                new Posicion("MSFT", new BigDecimal("5"), new BigDecimal("200.00"))));
        // 1000.00 + 1000.00
        assertEquals(new BigDecimal("2000.00"), p.costoTotal());
    }

    @Test
    @DisplayName("P&L total = valor total - costo total (ganancia)")
    void gananciaTotal() {
        Portafolio p = new Portafolio("u1", Arrays.asList(
                new Posicion("AAPL", new BigDecimal("10"), new BigDecimal("100.00")),
                new Posicion("MSFT", new BigDecimal("5"), new BigDecimal("200.00"))));

        Function<String, BigDecimal> precios = precios(Map.of(
                "AAPL", "150.00",
                "MSFT", "220.00"));
        // valor 2600.00 - costo 2000.00 = 600.00
        assertEquals(new BigDecimal("600.00"), p.gananciaPerdidaTotal(precios));
    }

    @Test
    @DisplayName("P&L total negativo cuando el mercado cae")
    void perdidaTotal() {
        Portafolio p = new Portafolio("u1", Arrays.asList(
                new Posicion("AAPL", new BigDecimal("10"), new BigDecimal("100.00"))));
        Function<String, BigDecimal> precios = precios(Map.of("AAPL", "80.00"));
        // valor 800.00 - costo 1000.00 = -200.00
        assertEquals(new BigDecimal("-200.00"), p.gananciaPerdidaTotal(precios));
    }

    @Test
    @DisplayName("las posiciones son inmutables desde el exterior")
    void posicionesInmutables() {
        Portafolio p = new Portafolio("u1", Arrays.asList(
                new Posicion("AAPL", BigDecimal.ONE, BigDecimal.ONE)));
        assertFalse(p.getPosiciones().isEmpty());
        try {
            p.getPosiciones().add(new Posicion("X", BigDecimal.ONE, BigDecimal.ONE));
        } catch (UnsupportedOperationException expected) {
            // ok: lista inmutable
        }
        assertEquals(1, p.getPosiciones().size());
    }
}
