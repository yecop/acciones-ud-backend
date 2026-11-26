package co.edu.udistrital.accionesud.notificaciones.application.port.in;

/** Reintenta los envíos por canal externo que fallaron temporalmente (SCRUM-51). */
public interface ReintentarEnviosUseCase {

    /** @return número de envíos intentados en este ciclo. */
    int reintentarPendientes();
}
