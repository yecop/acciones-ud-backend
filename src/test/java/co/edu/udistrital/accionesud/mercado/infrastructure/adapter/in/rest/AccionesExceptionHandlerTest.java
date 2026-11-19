package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import org.junit.jupiter.api.Test;

import static co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.AccionesExceptionHandler.segundosHastaProximaActualizacion;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Retry-After} del 502: depende del mercado y del intervalo configurado de su actualizador.
 */
class AccionesExceptionHandlerTest {

    @Test
    void usaElIntervaloDelActualizadorDeCadaMercado() {
        MercadosProperties properties = new MercadosProperties(); // 5 000 ms y 60 000 ms por defecto

        assertThat(segundosHastaProximaActualizacion(CodigoMercado.NYSE, properties)).isEqualTo(5);
        assertThat(segundosHastaProximaActualizacion(CodigoMercado.NASDAQ, properties)).isEqualTo(5);
        assertThat(segundosHastaProximaActualizacion(CodigoMercado.LSE, properties)).isEqualTo(60);
        assertThat(segundosHastaProximaActualizacion(CodigoMercado.TSE, properties)).isEqualTo(60);
        assertThat(segundosHastaProximaActualizacion(CodigoMercado.ASX, properties)).isEqualTo(60);
    }

    @Test
    void respetaLosIntervalosConfiguradosYRedondeaHaciaArriba() {
        MercadosProperties properties = new MercadosProperties();
        properties.getCotizaciones().setIntervaloMs(2_500);
        properties.getCotizaciones().getInternacional().setIntervaloMs(300_001);

        assertThat(segundosHastaProximaActualizacion(CodigoMercado.NYSE, properties)).isEqualTo(3);
        assertThat(segundosHastaProximaActualizacion(CodigoMercado.LSE, properties)).isEqualTo(301);
    }

    @Test
    void sinMercadoConocidoUsaElIntervaloMayorYNuncaMenosDeUnSegundo() {
        MercadosProperties properties = new MercadosProperties();
        assertThat(segundosHastaProximaActualizacion(null, properties)).isEqualTo(60);

        properties.getCotizaciones().setIntervaloMs(10);
        properties.getCotizaciones().getInternacional().setIntervaloMs(10);
        assertThat(segundosHastaProximaActualizacion(CodigoMercado.NYSE, properties)).isEqualTo(1);
    }
}
