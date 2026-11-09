package co.edu.udistrital.accionesud.mercado.application.service;

import co.edu.udistrital.accionesud.mercado.domain.exception.MercadoNoEncontradoException;
import co.edu.udistrital.accionesud.mercado.domain.model.CalendarioFeriados;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.EstadoMercadoSnapshot;
import co.edu.udistrital.accionesud.mercado.domain.model.HorarioOperacion;
import co.edu.udistrital.accionesud.mercado.domain.model.Mercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.MercadoCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas del servicio de aplicación usando un <strong>Mock del RelojPort</strong>
 * para simular y validar el comportamiento en distintos instantes.
 */
@ExtendWith(MockitoExtension.class)
class MercadoServiceTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Mock
    private RelojPort reloj;

    @Mock
    private MercadoCatalogoPort catalogo;

    private MercadoService service;

    @BeforeEach
    void setUp() {
        service = new MercadoService(catalogo, reloj, List.of());
    }

    private Mercado nyse() {
        return Mercado.conDiasHabilesEstandar(
                CodigoMercado.NYSE,
                "New York Stock Exchange",
                NY,
                HorarioOperacion.de(LocalTime.of(9, 30), LocalTime.of(16, 0)),
                CalendarioFeriados.vacio());
    }

    private Instant instanteNy(int anio, int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(anio, mes, dia, hora, minuto, 0, 0, NY).toInstant();
    }

    @Test
    @DisplayName("El reloj inyectado determina el estado: mercado ABIERTO")
    void relojControlaEstadoAbierto() {
        when(catalogo.buscarPorCodigo(CodigoMercado.NYSE)).thenReturn(Optional.of(nyse()));
        when(reloj.ahora()).thenReturn(instanteNy(2026, 1, 5, 11, 0)); // lunes 11:00

        EstadoMercadoSnapshot estado = service.consultarEstado(CodigoMercado.NYSE);

        assertThat(estado.abierto()).isTrue();
    }

    @Test
    @DisplayName("El reloj inyectado determina el estado: mercado CERRADO (fin de semana)")
    void relojControlaEstadoCerrado() {
        when(catalogo.buscarPorCodigo(CodigoMercado.NYSE)).thenReturn(Optional.of(nyse()));
        when(reloj.ahora()).thenReturn(instanteNy(2026, 1, 3, 11, 0)); // sábado

        EstadoMercadoSnapshot estado = service.consultarEstado(CodigoMercado.NYSE);

        assertThat(estado.abierto()).isFalse();
        assertThat(estado.proximaApertura()).contains(instanteNy(2026, 1, 5, 9, 30));
    }

    @Test
    @DisplayName("Un mercado inexistente lanza MercadoNoEncontradoException")
    void mercadoInexistente() {
        when(catalogo.buscarPorCodigo(CodigoMercado.LSE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarEstado(CodigoMercado.LSE))
                .isInstanceOf(MercadoNoEncontradoException.class);
    }
}
