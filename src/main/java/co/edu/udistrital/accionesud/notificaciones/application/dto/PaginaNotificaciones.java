package co.edu.udistrital.accionesud.notificaciones.application.dto;

import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;

import java.util.List;

/** Página de notificaciones, más recientes primero. */
public record PaginaNotificaciones(List<Notificacion> contenido, int pagina, int tamano, long totalElementos) {

    public PaginaNotificaciones {
        contenido = List.copyOf(contenido);
    }

    public int totalPaginas() {
        return (int) ((totalElementos + tamano - 1) / tamano);
    }
}
