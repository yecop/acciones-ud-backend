package co.edu.udistrital.accionesud.ordenes.infrastructure.config;

import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarAccionesUseCase;
import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarEstadoMercadoUseCase;
import co.edu.udistrital.accionesud.ordenes.application.port.in.SimularOrdenUseCase;
import co.edu.udistrital.accionesud.ordenes.application.port.out.ParametrosPort;
import co.edu.udistrital.accionesud.ordenes.application.port.out.PrecioSimulacionPort;
import co.edu.udistrital.accionesud.ordenes.application.service.SimularOrdenService;
import co.edu.udistrital.accionesud.ordenes.domain.service.CalculadoraComision;
import co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.out.mercado.PrecioSimulacionAdapter;
import co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.out.parametros.ParametrosSistemaJdbcAdapter;
import co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.out.parametros.ParametrosSistemaMemoriaAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class OrdenesBeansConfig {

    @Bean
    public CalculadoraComision calculadoraComision() {
        return new CalculadoraComision();
    }

    @Bean
    public PrecioSimulacionPort precioSimulacionPort(ConsultarAccionesUseCase acciones,
                                                     ConsultarEstadoMercadoUseCase mercados) {
        return new PrecioSimulacionAdapter(acciones, mercados);
    }

    @Bean
    public ParametrosPort parametrosPort(ObjectProvider<JdbcTemplate> plantillas) {
        JdbcTemplate jdbc = plantillas.getIfUnique();
        return jdbc == null ? new ParametrosSistemaMemoriaAdapter()
                : new ParametrosSistemaJdbcAdapter(jdbc, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    @Bean
    public SimularOrdenUseCase simularOrdenUseCase(PrecioSimulacionPort precios,
                                                   ParametrosPort parametros,
                                                   ConsultarSaldoUseCase saldos,
                                                   CalculadoraComision calculadora) {
        return new SimularOrdenService(precios, parametros, saldos, calculadora);
    }
}
