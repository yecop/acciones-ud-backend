package co.edu.udistrital.accionesud.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DesafioMfa(
        UUID id,
        UUID usuarioId,
        String tokenHash,
        String codigoHash,
        CanalMfa canal,
        Instant creadoEn,
        Instant expiraEn,
        int intentos,
        int maxIntentos,
        Instant bloqueadoHasta,
        int reenvios,
        int maxReenvios,
        Instant ultimoEnvioEn,
        Instant usadoEn) {

    public DesafioMfa {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(codigoHash, "codigoHash");
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(expiraEn, "expiraEn");
    }

    public boolean estaExpirado(Instant ahora) {
        return !ahora.isBefore(expiraEn);
    }

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    public boolean estaUsado() {
        return usadoEn != null;
    }

    public DesafioMfa conIntentoFallido(int nuevosIntentos, Instant nuevoBloqueo) {
        return new DesafioMfa(id, usuarioId, tokenHash, codigoHash, canal, creadoEn, expiraEn,
                nuevosIntentos, maxIntentos, nuevoBloqueo, reenvios, maxReenvios, ultimoEnvioEn, usadoEn);
    }

    public DesafioMfa usado(Instant momento) {
        return new DesafioMfa(id, usuarioId, tokenHash, codigoHash, canal, creadoEn, expiraEn,
                intentos, maxIntentos, bloqueadoHasta, reenvios, maxReenvios, ultimoEnvioEn, momento);
    }

    public DesafioMfa reenviado(String nuevoCodigoHash, Instant nuevaExpiracion, Instant momento) {
        return new DesafioMfa(id, usuarioId, tokenHash, nuevoCodigoHash, canal, creadoEn,
                nuevaExpiracion, intentos, maxIntentos, bloqueadoHasta, reenvios + 1, maxReenvios,
                momento, usadoEn);
    }
}
