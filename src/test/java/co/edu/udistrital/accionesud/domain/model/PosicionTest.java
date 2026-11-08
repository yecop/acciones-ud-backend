package co.edu.udistrital.accionesud.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias exhaustivas de los calculos matematicos de la posicion:
 * costo base, valor actual, ganancia/perdida y porcentaje (SCRUM-25).
 */
@DisplayName("Posicion - calculos financieros")
class PosicionTest {

    private static Posicion posicion(String activo, String cantidad, String precioCompra) {
        return new Posicion(activo, new BigDecimal(cantidad), new BigDecimal(precioCompra));
    }

    @Nested
    @DisplayName("Costo base")
    class CostoBase {

        @Test
        @DisplayName("costoBase = cantidad * precioCompra con 2 decimales")
        void calculaCostoBase() {
            Posicion p = posicion("AAPL", "10", "150.00");
            assertEquals(new BigDecimal("1500.00"), p.costoBase());
        }

        @Test
        @DisplayName("redondea a 2 decimales HALF_UP")
        void redondeaCostoBase() {
            // 3 * 10.005 = 30.015 -> 30.02 (HALF_UP)
            Posicion p = posicion("AAPL", "3", "10.005");
            assertEquals(new BigDecimal("30.02"), p.costoBase());
        }

        @Test
        @DisplayName("cantidad cero produce costo base 0.00")
        void costoBaseCantidadCero() {
            Posicion p = posicion("AAPL", "0", "150.00");
            assertEquals(new BigDecimal("0.00"), p.costoBase());
        }
    }

    @Nested
    @DisplayName("Valor actual")
    class ValorActual {

        @Test
        @DisplayName("valorActual = cantidad * precioActual con 2 decimales")
        void calculaValorActual() {
            Posicion p = posicion("AAPL", "10", "150.00");
            assertEquals(new BigDecimal("1900.00"), p.valorActual(new BigDecimal("190.00")));
        }

        @Test
        @DisplayName("redondea a 2 decimales HALF_UP")
        void redondeaValorActual() {
            // 7 * 12.344 = 86.408 -> 86.41
            Posicion p = posicion("AAPL", "7", "10.00");
            assertEquals(new BigDecimal("86.41"), p.valorActual(new BigDecimal("12.344")));
        }
    }

    @Nested
    @DisplayName("Ganancia / Perdida")
    class GananciaPerdida {

        @Test
        @DisplayName("ganancia positiva cuando el precio sube")
        void gananciaPositiva() {
            // costo 1500.00, valor 1900.00 -> +400.00
            Posicion p = posicion("AAPL", "10", "150.00");
            assertEquals(new BigDecimal("400.00"), p.gananciaPerdida(new BigDecimal("190.00")));
        }

        @Test
        @DisplayName("perdida negativa cuando el precio baja")
        void perdidaNegativa() {
            // costo 1500.00, valor 1200.00 -> -300.00
            Posicion p = posicion("AAPL", "10", "150.00");
            assertEquals(new BigDecimal("-300.00"), p.gananciaPerdida(new BigDecimal("120.00")));
        }

        @Test
        @DisplayName("sin cambio de precio -> 0.00")
        void sinCambio() {
            Posicion p = posicion("AAPL", "10", "150.00");
            assertEquals(new BigDecimal("0.00"), p.gananciaPerdida(new BigDecimal("150.00")));
        }
    }

    @Nested
    @DisplayName("Porcentaje de ganancia / perdida")
    class Porcentaje {

        @Test
        @DisplayName("porcentaje de ganancia")
        void porcentajeGanancia() {
            // P&L 400 sobre costo 1500 -> 26.67%
            Posicion p = posicion("AAPL", "10", "150.00");
            assertEquals(new BigDecimal("26.67"),
                    p.porcentajeGananciaPerdida(new BigDecimal("190.00")));
        }

        @Test
        @DisplayName("porcentaje de perdida negativo")
        void porcentajePerdida() {
            // P&L -300 sobre costo 1500 -> -20.00%
            Posicion p = posicion("AAPL", "10", "150.00");
            assertEquals(new BigDecimal("-20.00"),
                    p.porcentajeGananciaPerdida(new BigDecimal("120.00")));
        }

        @Test
        @DisplayName("ganancia de 100% cuando el precio se duplica")
        void porcentajeCien() {
            Posicion p = posicion("AAPL", "5", "100.00");
            assertEquals(new BigDecimal("100.00"),
                    p.porcentajeGananciaPerdida(new BigDecimal("200.00")));
        }

        @Test
        @DisplayName("costo base cero -> porcentaje 0.00 (sin division por cero)")
        void porcentajeCostoCero() {
            Posicion p = posicion("AAPL", "0", "100.00");
            assertEquals(new BigDecimal("0.00"),
                    p.porcentajeGananciaPerdida(new BigDecimal("200.00")));
        }

        @ParameterizedTest(name = "cant={0}, compra={1}, actual={2} -> pct={3}")
        @CsvSource({
                "10, 100.00, 110.00, 10.00",
                "10, 100.00, 90.00, -10.00",
                "3,  33.33,  66.66, 100.00",
                "1,  200.00, 250.00, 25.00",
                "8,  12.50,  12.50, 0.00"
        })
        @DisplayName("tabla de casos de porcentaje")
        void tablaPorcentajes(String cantidad, String compra, String actual, String esperado) {
            Posicion p = posicion("XYZ", cantidad, compra);
            assertEquals(new BigDecimal(esperado),
                    p.porcentajeGananciaPerdida(new BigDecimal(actual)));
        }
    }

    @Nested
    @DisplayName("Validaciones de construccion")
    class Validaciones {

        @Test
        @DisplayName("activo nulo lanza NPE")
        void activoNulo() {
            assertThrows(NullPointerException.class,
                    () -> new Posicion(null, BigDecimal.ONE, BigDecimal.ONE));
        }

        @Test
        @DisplayName("activo vacio lanza IllegalArgumentException")
        void activoVacio() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Posicion("  ", BigDecimal.ONE, BigDecimal.ONE));
        }

        @Test
        @DisplayName("cantidad negativa lanza IllegalArgumentException")
        void cantidadNegativa() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Posicion("AAPL", new BigDecimal("-1"), BigDecimal.ONE));
        }

        @Test
        @DisplayName("precio actual nulo en valorActual lanza NPE")
        void precioActualNulo() {
            Posicion p = posicion("AAPL", "1", "1.00");
            assertThrows(NullPointerException.class, () -> p.valorActual(null));
        }

        @Test
        @DisplayName("el activo se normaliza a mayusculas y sin espacios")
        void normalizaActivo() {
            Posicion p = new Posicion(" aapl ", BigDecimal.ONE, BigDecimal.ONE);
            assertEquals("AAPL", p.getActivo());
        }

        @Test
        @DisplayName("precios con muchos decimales no pierden precision antes de redondear")
        void precisionBigDecimal() {
            Posicion p = posicion("AAPL", "1000000", "0.07");
            assertTrue(p.costoBase().compareTo(new BigDecimal("70000.00")) == 0);
        }
    }
}
