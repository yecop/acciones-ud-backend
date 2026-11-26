package co.edu.udistrital.accionesud.notificaciones.application.dto;

import co.edu.udistrital.accionesud.notificaciones.domain.model.TipoNotificacion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Consulta de la bandeja de un usuario (SCRUM-51). {@code tipo}, {@code desde} y {@code leida}
 * son opcionales; la página empieza en 0.
 */
public record FiltroNotificaciones(UUID usuarioId, TipoNotificacion tipo, Instant desde, Boolean leida,
                                   int pagina, int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    public FiltroNotificaciones {
        Objects.requireNonNull(usuarioId, "El usuario es obligatorio");
        if (pagina < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("El tamaño debe estar entre 1 y " + TAMANO_MAXIMO);
        }
    }

    public long desplazamiento() {
        return (long) pagina * tamano;
    }
}
