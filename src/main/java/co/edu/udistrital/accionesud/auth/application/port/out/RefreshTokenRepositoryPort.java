package co.edu.udistrital.accionesud.auth.application.port.out;

import co.edu.udistrital.accionesud.auth.domain.model.TokenRefresco;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

    void guardar(TokenRefresco token);

    Optional<TokenRefresco> buscarPorHash(String tokenHash);

    boolean marcarUsado(String tokenHash, Instant momento);

    void revocarFamilia(UUID familiaId, Instant momento);
}
