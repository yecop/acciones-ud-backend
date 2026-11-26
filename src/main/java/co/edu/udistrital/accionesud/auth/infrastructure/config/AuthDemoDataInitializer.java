package co.edu.udistrital.accionesud.auth.infrastructure.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "accionesud.auth.demo", name = "habilitado", havingValue = "true")
public class AuthDemoDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public AuthDemoDataInitializer(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                   AuthProperties properties) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String correo = properties.getDemo().getCorreo();
        Integer existentes = jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE lower(correo) = lower(?)",
                Integer.class, correo);
        if (existentes == null || existentes == 0) {
            jdbc.update("""
                    INSERT INTO usuario (
                        id, rol_id, estado_usuario_id, nombre, correo, contrasena_hash,
                        celular, mfa_activo)
                    VALUES (?, 1, 1, 'Inversionista Demo', ?, ?, NULL, TRUE)
                    """,
                    UUID.randomUUID(),
                    correo,
                    passwordEncoder.encode(properties.getDemo().getClave()));
        }
        asegurarCuenta(correo);
    }

    /**
     * El demo es un INVERSIONISTA: necesita su perfil de inversionista y su cuenta de corretaje
     * para tener saldo y movimientos (SCRUM-46). Se completa también en bases donde el usuario ya
     * existía sin ellos.
     */
    private void asegurarCuenta(String correo) {
        UUID id = jdbc.queryForObject("SELECT id FROM usuario WHERE lower(correo) = lower(?)", UUID.class, correo);
        jdbc.update("""
                INSERT INTO inversionista (usuario_id, nivel_experiencia_id)
                SELECT ?, min(id) FROM nivel_experiencia
                ON CONFLICT (usuario_id) DO NOTHING""", id);
        jdbc.update("""
                INSERT INTO cuenta_corretaje (id, inversionista_id) VALUES (?, ?)
                ON CONFLICT (inversionista_id) DO NOTHING""", UUID.randomUUID(), id);
    }
}
