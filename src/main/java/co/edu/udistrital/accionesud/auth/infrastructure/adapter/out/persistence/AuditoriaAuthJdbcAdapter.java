package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.auth.application.port.out.AuditoriaAuthPort;
import co.edu.udistrital.accionesud.auth.domain.model.RegistroIntento;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@Profile("!test")
public class AuditoriaAuthJdbcAdapter implements AuditoriaAuthPort {

    private final JdbcTemplate jdbc;

    public AuditoriaAuthJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(RegistroIntento intento) {
        jdbc.update("""
                INSERT INTO auth_intento (
                    usuario_id, correo, tipo, resultado, motivo, ip, user_agent, trace_id, creado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                intento.usuarioId(), intento.correo(), intento.tipo().name(),
                intento.resultado().name(), intento.motivo(), intento.ip(), intento.userAgent(),
                intento.traceId(), Timestamp.from(intento.creadoEn()));
    }

    @Override
    public int contarFallosLogin(String correo, String ip, Instant desde) {
        Integer total = jdbc.queryForObject("""
                SELECT count(*)
                FROM auth_intento
                WHERE tipo = 'LOGIN' AND resultado = 'FALLIDO'
                  AND lower(correo) = lower(?)
                  AND creado_en >= ?
                  AND (? = '' OR ip = ?)
                """, Integer.class, correo, Timestamp.from(desde), ip, ip);
        return total == null ? 0 : total;
    }
}
