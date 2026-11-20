package co.edu.udistrital.accionesud.perfil.infrastructure.config;

import co.edu.udistrital.accionesud.perfil.application.port.out.PerfilRepositoryPort;
import co.edu.udistrital.accionesud.perfil.application.service.PerfilService;
import co.edu.udistrital.accionesud.perfil.infrastructure.adapter.out.persistence.PerfilJdbcRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

// Perfil test sin base de datos: estos beans no se crean allí.
@Configuration
@Profile("!test")
public class PerfilBeansConfig {

    @Bean
    public PerfilRepositoryPort perfilRepositoryPort(JdbcTemplate jdbc) {
        return new PerfilJdbcRepository(jdbc);
    }

    @Bean
    public PerfilService perfilService(PerfilRepositoryPort repositorio) {
        return new PerfilService(repositorio);
    }
}