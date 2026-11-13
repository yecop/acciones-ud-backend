package co.edu.udistrital.accionesud.auth.application.port.out;

import co.edu.udistrital.accionesud.auth.domain.model.DesafioMfa;

import java.util.Optional;

public interface MfaRepositoryPort {

    void guardar(DesafioMfa desafio);

    Optional<DesafioMfa> buscarPorTokenHash(String tokenHash);

    void actualizar(DesafioMfa desafio);
}
