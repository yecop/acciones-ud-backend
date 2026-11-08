package co.edu.udistrital.accionesud.cuenta.application.port.out;

import co.edu.udistrital.accionesud.cuenta.domain.model.Portafolio;

import java.util.Optional;

/**
 * Puerto de salida: fuente de persistencia del portafolio de un usuario.
 * La implementacion concreta (adaptador) vive en la capa de infraestructura.
 */
public interface PortafolioRepositoryPort {

    /**
     * Busca el portafolio de un usuario.
     *
     * @param usuarioId identificador del usuario.
     * @return el portafolio si existe; vacio si el usuario es nuevo.
     */
    Optional<Portafolio> buscarPorUsuario(String usuarioId);
}
