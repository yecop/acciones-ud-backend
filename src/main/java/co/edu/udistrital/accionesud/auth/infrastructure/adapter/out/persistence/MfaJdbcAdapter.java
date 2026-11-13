package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.auth.application.port.out.MfaRepositoryPort;
import co.edu.udistrital.accionesud.auth.domain.model.CanalMfa;
import co.edu.udistrital.accionesud.auth.domain.model.DesafioMfa;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!test")
public class MfaJdbcAdapter implements MfaRepositoryPort {

    private static final RowMapper<DesafioMfa> MAPPER = (rs, rowNum) -> new DesafioMfa(
            rs.getObject("id", java.util.UUID.class),
            rs.getObject("usuario_id", java.util.UUID.class),
            rs.getString("token_hash"),
            rs.getString("codigo_hash"),
            CanalMfa.valueOf(rs.getString("canal")),
            instante(rs.getTimestamp("creado_en")),
            instante(rs.getTimestamp("expira_en")),
            rs.getInt("intentos"),
            rs.getInt("max_intentos"),
            instante(rs.getTimestamp("bloqueado_hasta")),
            rs.getInt("reenvios"),
            rs.getInt("max_reenvios"),
            instante(rs.getTimestamp("ultimo_envio_en")),
            instante(rs.getTimestamp("usado_en")));

    private final JdbcTemplate jdbc;

    public MfaJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(DesafioMfa desafio) {
        jdbc.update("""
                INSERT INTO auth_mfa_desafio (
                    id, usuario_id, token_hash, codigo_hash, canal, creado_en, expira_en,
                    intentos, max_intentos, bloqueado_hasta, reenvios, max_reenvios,
                    ultimo_envio_en, usado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                desafio.id(), desafio.usuarioId(), desafio.tokenHash(), desafio.codigoHash(),
                desafio.canal().name(), timestamp(desafio.creadoEn()),
                timestamp(desafio.expiraEn()), desafio.intentos(), desafio.maxIntentos(),
                timestamp(desafio.bloqueadoHasta()), desafio.reenvios(), desafio.maxReenvios(),
                timestamp(desafio.ultimoEnvioEn()), timestamp(desafio.usadoEn()));
    }

    @Override
    public Optional<DesafioMfa> buscarPorTokenHash(String tokenHash) {
        List<DesafioMfa> desafios = jdbc.query("""
                SELECT id, usuario_id, token_hash, codigo_hash, canal, creado_en, expira_en,
                       intentos, max_intentos, bloqueado_hasta, reenvios, max_reenvios,
                       ultimo_envio_en, usado_en
                FROM auth_mfa_desafio
                WHERE token_hash = ?
                """, MAPPER, tokenHash);
        return desafios.stream().findFirst();
    }

    @Override
    public void actualizar(DesafioMfa desafio) {
        jdbc.update("""
                UPDATE auth_mfa_desafio
                SET codigo_hash = ?, expira_en = ?, intentos = ?, bloqueado_hasta = ?,
                    reenvios = ?, ultimo_envio_en = ?, usado_en = ?
                WHERE id = ?
                """,
                desafio.codigoHash(), timestamp(desafio.expiraEn()), desafio.intentos(),
                timestamp(desafio.bloqueadoHasta()), desafio.reenvios(),
                timestamp(desafio.ultimoEnvioEn()), timestamp(desafio.usadoEn()), desafio.id());
    }

    private static Instant instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
