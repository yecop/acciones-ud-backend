package co.edu.udistrital.accionesud.notificaciones.application.port.out;

import co.edu.udistrital.accionesud.notificaciones.application.dto.FiltroNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.dto.PaginaNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificacionRepositoryPort {

    /**
     * Inserta la notificación si no existe otra con el mismo (usuario, evento, tipo).
     *
     * @return {@code true} si se insertó; {@code false} si ya estaba (evento reprocesado).
     */
    boolean guardarSiNoExiste(Notificacion notificacion);

    /** Busca solo entre las notificaciones del usuario. */
    Optional<Notificacion> buscarDelUsuario(UUID notificacionId, UUID usuarioId);

    Optional<Notificacion> buscar(UUID notificacionId);

    /** Marca como leída si aún no lo estaba; no cambia la fecha de una ya leída. */
    void marcarLeida(UUID notificacionId, UUID usuarioId, Instant leidaEn);

    int marcarTodasLeidas(UUID usuarioId, Instant leidaEn);

    PaginaNotificaciones listar(FiltroNotificaciones filtro);
}
