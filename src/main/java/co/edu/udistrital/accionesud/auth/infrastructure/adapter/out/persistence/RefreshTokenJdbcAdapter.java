package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.auth.application.port.out.RefreshTokenRepositoryPort;
import co.edu.udistrital.accionesud.auth.domain.model.TokenRefresco;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class RefreshTokenJdbcAdapter implements RefreshTokenRepositoryPort {

    private static final RowMapper<TokenRefresco> MAPPER = (rs, rowNum) -> new TokenRefresco(
            rs.getObject("id", UUID.class),
            rs.getObject("usuario_id", UUID.class),
            rs.getString("token_hash"),
            rs.getObject("familia_id", UUID.class),
            instante(rs.getTimestamp("creado_en")),
            instante(rs.getTimestamp("expira_en")),
            instante(rs.getTimestamp("usado_en")),
            instante(rs.getTimestamp("revocado_en")));

    private final JdbcTemplate jdbc;

    public RefreshTokenJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(TokenRefresco token) {
        jdbc.update("""
                INSERT INTO auth_token_refresco (
                    id, usuario_id, token_hash, familia_id, creado_en, expira_en, usado_en,
                    revocado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                token.id(), token.usuarioId(), token.tokenHash(), token.familiaId(),
                timestamp(token.creadoEn()), timestamp(token.expiraEn()),
                timestamp(token.usadoEn()), timestamp(token.revocadoEn()));
    }

    @Override
    public Optional<TokenRefresco> buscarPorHash(String tokenHash) {
        List<TokenRefresco> tokens = jdbc.query("""
                SELECT id, usuario_id, token_hash, familia_id, creado_en, expira_en, usado_en,
                       revocado_en
                FROM auth_token_refresco
                WHERE token_hash = ?
                """, MAPPER, tokenHash);
        return tokens.stream().findFirst();
    }

    @Override
    public boolean marcarUsado(String tokenHash, Instant momento) {
        return jdbc.update("""
                UPDATE auth_token_refresco
                SET usado_en = ?
                WHERE token_hash = ? AND usado_en IS NULL AND revocado_en IS NULL
                """, timestamp(momento), tokenHash) > 0;
    }

    @Override
    public void revocarFamilia(UUID familiaId, Instant momento) {
        jdbc.update("""
                UPDATE auth_token_refresco
                SET revocado_en = ?
                WHERE familia_id = ? AND revocado_en IS NULL
                """, timestamp(momento), familiaId);
    }

    private static Instant instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
