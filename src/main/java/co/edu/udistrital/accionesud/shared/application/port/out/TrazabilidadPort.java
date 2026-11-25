package co.edu.udistrital.accionesud.shared.application.port.out;

/**
 * Entrega el traceId de la solicitud en curso a la capa de aplicación sin depender del
 * servlet ni del MDC (SCRUM-33).
 */
public interface TrazabilidadPort {

    /** traceId actual; nunca vacío (fuera de una solicitud HTTP se genera uno). */
    String traceIdActual();
}
