package co.edu.udistrital.accionesud.infrastructure.config;

import co.edu.udistrital.accionesud.application.port.in.ConsultarPortafolioUseCase;
import co.edu.udistrital.accionesud.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.application.port.out.PortafolioRepositoryPort;
import co.edu.udistrital.accionesud.application.port.out.PrecioCachePort;
import co.edu.udistrital.accionesud.application.port.out.SaldoRepositoryPort;
import co.edu.udistrital.accionesud.application.service.PortafolioService;
import co.edu.udistrital.accionesud.application.service.SaldoService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composicion de dependencias (composition root). Aqui la infraestructura
 * ensambla los servicios de aplicacion con sus puertos de salida, manteniendo
 * las capas de dominio y aplicacion libres de anotaciones de Spring.
 */
@Configuration
@EnableConfigurationProperties(PlataformaProperties.class)
public class BeanConfiguration {

    @Bean
    public ConsultarSaldoUseCase consultarSaldoUseCase(SaldoRepositoryPort saldoRepository,
                                                       PlataformaProperties properties) {
        return new SaldoService(saldoRepository, properties.getSaldoInicialSimulado());
    }

    @Bean
    public ConsultarPortafolioUseCase consultarPortafolioUseCase(
            PortafolioRepositoryPort portafolioRepository,
            PrecioCachePort precioCache) {
        return new PortafolioService(portafolioRepository, precioCache);
    }
}
