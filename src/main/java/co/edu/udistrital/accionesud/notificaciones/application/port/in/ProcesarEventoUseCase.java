package co.edu.udistrital.accionesud.notificaciones.application.port.in;

import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;

/**
 * Crea las notificaciones de un evento de dominio (SCRUM-51). Es idempotente: procesar el mismo
 * evento dos veces no duplica notificaciones ni envíos.
 */
public interface ProcesarEventoUseCase {

    /** @return número de notificaciones nuevas creadas. */
    int procesar(EventoDominio evento);
}
