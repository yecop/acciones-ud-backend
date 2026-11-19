package co.edu.udistrital.accionesud.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TokenRefresco(
        UUID id,
        UUID usuarioId,
        String tokenHash,
        UUID familiaId,
        Instant creadoEn,
        Instant expiraEn,
        Instant usadoEn,
        Instant revocadoEn) {

    public TokenRefresco {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(familiaId, "familiaId");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(expiraEn, "expiraEn");
    }

    public boolean estaExpirado(Instant ahora) {
        return !ahora.isBefore(expiraEn);
    }

    public boolean estaUsado() {
        return usadoEn != null;
    }

    public boolean estaRevocado() {
        return revocadoEn != null;
    }
}
