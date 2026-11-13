package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.udistrital.accionesud.auth.domain.model.Usuario;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class UsuarioJdbcAdapter implements UsuarioRepositoryPort {

    private static final String SELECT_USUARIO = """
            SELECT u.id, u.nombre, u.correo, u.contrasena_hash,
                   r.nombre AS rol,
                   CASE WHEN eu.nombre = 'ACTIVO' THEN TRUE ELSE FALSE END AS activo
            FROM usuario u
            JOIN rol r ON r.id = u.rol_id
            JOIN estado_usuario eu ON eu.id = u.estado_usuario_id
            """;

    private static final RowMapper<Usuario> MAPPER = (rs, rowNum) -> new Usuario(
            rs.getObject("id", UUID.class),
            rs.getString("nombre"),
            rs.getString("correo"),
            rs.getString("contrasena_hash"),
            rs.getString("rol"),
            rs.getBoolean("activo"));

    private final JdbcTemplate jdbc;

    public UsuarioJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        List<Usuario> usuarios = jdbc.query(
                SELECT_USUARIO + " WHERE lower(u.correo) = lower(?)", MAPPER, correo);
        return usuarios.stream().findFirst();
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        List<Usuario> usuarios = jdbc.query(
                SELECT_USUARIO + " WHERE u.id = ?", MAPPER, id);
        return usuarios.stream().findFirst();
    }
}
