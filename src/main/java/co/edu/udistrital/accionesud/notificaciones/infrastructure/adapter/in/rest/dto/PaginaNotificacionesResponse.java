package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.notificaciones.application.dto.PaginaNotificaciones;

import java.util.List;

public record PaginaNotificacionesResponse(List<NotificacionResponse> contenido, int pagina, int tamano,
                                           long totalElementos, int totalPaginas) {

    public static PaginaNotificacionesResponse desde(PaginaNotificaciones p) {
        return new PaginaNotificacionesResponse(p.contenido().stream().map(NotificacionResponse::desde).toList(),
                p.pagina(), p.tamano(), p.totalElementos(), p.totalPaginas());
    }
}
