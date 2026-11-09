package co.edu.udistrital.accionesud.mercado.infrastructure.config;

import co.edu.udistrital.accionesud.mercado.application.service.MercadoService;
import co.edu.udistrital.accionesud.mercado.domain.port.out.MercadoCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de wiring del módulo de mercados.
 *
 * <p>Ensambla el servicio de aplicación (que es tecnología-agnóstico) con los
 * adaptadores de salida mediante inyección de dependencias.</p>
 */
@Configuration
@EnableConfigurationProperties(MercadosProperties.class)
public class MercadoBeansConfig {

    @Bean
    public MercadoService mercadoService(MercadoCatalogoPort catalogo,
                                         RelojPort reloj,
                                         List<ProveedorDatosMercadoPort> proveedores) {
        return new MercadoService(catalogo, reloj, proveedores);
    }
}
