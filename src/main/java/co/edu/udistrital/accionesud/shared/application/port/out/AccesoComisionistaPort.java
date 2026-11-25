package co.edu.udistrital.accionesud.shared.application.port.out;

import co.edu.udistrital.accionesud.shared.domain.exception.AccesoDenegadoException;

import java.util.Optional;
import java.util.UUID;

/**
 * Verifica en el backend si un comisionista puede ver u operar la información de un
 * inversionista (SCRUM-33). Lo implementa SCRUM-52 con las asociaciones vigentes.
 */
public interface AccesoComisionistaPort {

    /** {@code true} si existe una relación vigente entre ambos. */
    boolean puedeAcceder(UUID comisionistaId, UUID inversionistaId);

    /** Comisionista con relación vigente del inversionista, si tiene. */
    Optional<UUID> comisionistaVigenteDe(UUID inversionistaId);

    /** @throws AccesoDenegadoException (HTTP 403 {@code ACCESO_DENEGADO}) si no hay relación vigente. */
    default void exigirAcceso(UUID comisionistaId, UUID inversionistaId) {
        if (!puedeAcceder(comisionistaId, inversionistaId)) {
            throw new AccesoDenegadoException("El comisionista no tiene una relación vigente con este inversionista");
        }
    }
}
