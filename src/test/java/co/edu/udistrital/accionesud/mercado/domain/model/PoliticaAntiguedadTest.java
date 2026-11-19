package co.edu.udistrital.accionesud.mercado.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaAntiguedadTest {

    private static final Instant ACTUALIZADO = Instant.parse("2026-01-05T15:00:00Z");
    private static final Accion AAPL = new Accion("AAPL", "Apple Inc.", CodigoMercado.NASDAQ, Sector.TECNOLOGIA, "USD");
    private static final Accion BP = new Accion("BP", "BP plc", CodigoMercado.LSE, Sector.ENERGIA, "GBP");

    private final PoliticaAntiguedad politica =
            new PoliticaAntiguedad(Duration.ofSeconds(15), Duration.ofMinutes(30));

    private static Cotizacion cotizacion(String ticker) {
        return Cotizacion.de(ticker, new BigDecimal("190.10"), new BigDecimal("190.00"),
                new BigDecimal("190.20"), 1000, ACTUALIZADO, "ALPACA_IEX");
    }

    @Test
    void datoRecienteNoLlevaAdvertencia() {
        CotizacionVigente vigente = politica.evaluar(AAPL, cotizacion("AAPL"), ACTUALIZADO.plusSeconds(15), true);

        assertThat(vigente.desactualizada()).isFalse();
        assertThat(vigente.advertencia()).isNull();
        assertThat(vigente.antiguedadSegundos()).isEqualTo(15);
    }

    @Test
    void datoDeEstadosUnidosConMasDe15SegundosLlevaAdvertencia() {
        CotizacionVigente vigente = politica.evaluar(AAPL, cotizacion("AAPL"),
                ACTUALIZADO.plusSeconds(15).plusMillis(1), true);

        assertThat(vigente.desactualizada()).isTrue();
        assertThat(vigente.advertencia().codigo()).isEqualTo(Advertencia.DATO_DESACTUALIZADO);
    }

    @Test
    void proveedorCaidoSirveElUltimoDatoConAdvertencia() {
        CotizacionVigente vigente = politica.evaluar(AAPL, cotizacion("AAPL"), ACTUALIZADO.plusSeconds(3), false);

        assertThat(vigente.desactualizada()).isTrue();
        assertThat(vigente.advertencia().codigo()).isEqualTo(Advertencia.PROVEEDOR_NO_DISPONIBLE);
        assertThat(vigente.cotizacion().actualizadoEn()).isEqualTo(ACTUALIZADO);
    }

    @Test
    void losMercadosInternacionalesUsanSuPropioUmbral() {
        CotizacionVigente diezMinutos = politica.evaluar(BP, cotizacion("BP"), ACTUALIZADO.plusSeconds(600), true);
        assertThat(diezMinutos.desactualizada()).isFalse();
        assertThat(diezMinutos.umbralAntiguedadSegundos()).isEqualTo(1800);

        CotizacionVigente unaHora = politica.evaluar(BP, cotizacion("BP"), ACTUALIZADO.plusSeconds(3600), true);
        assertThat(unaHora.desactualizada()).isTrue();
        assertThat(unaHora.advertencia().codigo()).isEqualTo(Advertencia.DATO_DESACTUALIZADO);
    }

    @Test
    void proveedorInternacionalCaidoTambienAdvierte() {
        CotizacionVigente vigente = politica.evaluar(BP, cotizacion("BP"), ACTUALIZADO.plusSeconds(5), false);
        assertThat(vigente.advertencia().codigo()).isEqualTo(Advertencia.PROVEEDOR_NO_DISPONIBLE);
    }

    @Test
    void elSpreadSeDerivaDelBidYElAsk() {
        assertThat(cotizacion("AAPL").spread()).isEqualByComparingTo("0.20");
        assertThat(Cotizacion.de("AAPL", BigDecimal.ONE, null, BigDecimal.TEN, 0, ACTUALIZADO, "X").spread()).isNull();
    }
}
