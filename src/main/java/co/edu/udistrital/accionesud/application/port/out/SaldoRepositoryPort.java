package co.edu.udistrital.accionesud.application.port.out;

import co.edu.udistrital.accionesud.domain.model.Saldo;

import java.util.Optional;

/**
 * Puerto de salida: fuente de persistencia del saldo de una cuenta.
 * La implementacion concreta (adaptador) vive en la capa de infraestructura.
 */
public interface SaldoRepositoryPort {

    /**
     * Busca el saldo de un usuario.
     *
     * @param usuarioId identificador del usuario.
     * @return el saldo si existe; vacio si el usuario es nuevo.
     */
    Optional<Saldo> buscarPorUsuario(String usuarioId);
}
