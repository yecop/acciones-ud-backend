package co.edu.udistrital.accionesud.notificaciones.application.port.in;

import co.edu.udistrital.accionesud.notificaciones.application.dto.FiltroNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.dto.PaginaNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;

import java.util.UUID;

/** Bandeja del usuario autenticado: solo ve y modifica sus propias notificaciones (SCRUM-51). */
public interface ConsultarNotificacionesUseCase {

    PaginaNotificaciones listar(FiltroNotificaciones filtro);

    /** Idempotente. Una notificación ajena o inexistente lanza {@code NotificacionNoEncontradaException}. */
    Notificacion marcarLeida(UUID usuarioId, UUID notificacionId);

    /** Marca todas las no leídas del usuario y devuelve cuántas cambiaron. */
    int marcarTodasLeidas(UUID usuarioId);
}
