package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.auth.application.dto.NuevoUsuario;
import co.edu.udistrital.accionesud.auth.application.dto.SolicitudAlta;
import co.edu.udistrital.accionesud.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.udistrital.accionesud.auth.domain.exception.CorreoDuplicadoException;
import co.edu.udistrital.accionesud.auth.domain.exception.InteresNoValidoException;
import co.edu.udistrital.accionesud.auth.domain.model.EstadoUsuario;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class UsuarioJdbcRepository implements UsuarioRepositoryPort {

    private static final int ROL_INVERSIONISTA = 1;

    private final JdbcTemplate jdbc;

    public UsuarioJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeCorreo(String correo) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuario WHERE correo = ?)", Boolean.class, correo);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    @Transactional
    public void registrarPendiente(NuevoUsuario n) {
        try {
            jdbc.update("""
                    INSERT INTO usuario (id, rol_id, estado_usuario_id, nombre, apellido, correo,
                                         contrasena_hash, celular, fecha_nacimiento)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    n.usuarioId(), ROL_INVERSIONISTA, EstadoUsuario.PENDIENTE_ALTA.id(),
                    n.nombres(), n.apellidos(), n.correo(), n.contrasenaHash(),
                    n.celular(), n.fechaNacimiento());
        } catch (DuplicateKeyException e) {
            throw new CorreoDuplicadoException();
        }

        jdbc.update("""
                INSERT INTO inversionista (usuario_id, nivel_experiencia_id, nivel_riesgo, objetivos_financieros)
                VALUES (?, ?, ?, ?)""",
                n.usuarioId(), n.nivelExperiencia().id(), n.nivelRiesgo().name(), n.objetivos());

        for (String codigo : normalizar(n.mercados())) {
            int filas = jdbc.update("""
                    INSERT INTO inversionista_interes_mercado (inversionista_id, mercado_id)
                    SELECT ?, id FROM mercado WHERE lower(codigo) = ?""", n.usuarioId(), codigo);
            if (filas == 0) {
                throw new InteresNoValidoException("mercado", codigo);
            }
        }
        for (String sector : normalizar(n.sectores())) {
            int filas = jdbc.update("""
                    INSERT INTO inversionista_interes_sector (inversionista_id, sector_id)
                    SELECT ?, id FROM sector WHERE lower(nombre) = ?""", n.usuarioId(), sector);
            if (filas == 0) {
                throw new InteresNoValidoException("sector", sector);
            }
        }

        jdbc.update("INSERT INTO cuenta_corretaje (id, inversionista_id) VALUES (?, ?)",
                n.cuentaId(), n.usuarioId());
    }

    @Override
    @Transactional
    public void marcarAltaCompleta(UUID usuarioId, String cuentaBroker) {
        jdbc.update("UPDATE cuenta_corretaje SET alpaca_account_id = ? WHERE inversionista_id = ?",
                cuentaBroker, usuarioId);
        jdbc.update("UPDATE usuario SET estado_usuario_id = ? WHERE id = ? AND estado_usuario_id = ?",
                EstadoUsuario.ACTIVO.id(), usuarioId, EstadoUsuario.PENDIENTE_ALTA.id());
    }

    @Override
    public List<SolicitudAlta> buscarAltasPendientes(int limite) {
        return jdbc.query("""
                SELECT u.id, u.nombre, u.apellido, u.correo, u.celular, u.fecha_nacimiento
                FROM usuario u
                JOIN cuenta_corretaje c ON c.inversionista_id = u.id
                WHERE u.estado_usuario_id = ? AND c.alpaca_account_id IS NULL
                ORDER BY u.creado_en
                LIMIT ?""",
                (rs, i) -> new SolicitudAlta(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("correo"),
                        rs.getString("celular"),
                        rs.getObject("fecha_nacimiento", LocalDate.class)),
                EstadoUsuario.PENDIENTE_ALTA.id(), limite);
    }

    private static Set<String> normalizar(Set<String> valores) {
        return valores.stream()
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}