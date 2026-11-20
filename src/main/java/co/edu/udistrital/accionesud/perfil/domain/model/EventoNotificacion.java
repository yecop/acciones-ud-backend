package co.edu.udistrital.accionesud.perfil.domain.model;

public enum EventoNotificacion {
    APERTURA_CIERRE_MERCADO(1), RESUMEN_DIARIO_OPERACIONES(2), ORDEN_EJECUTADA(3);

    private final int id;

    EventoNotificacion(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static EventoNotificacion desdeId(int id) {
        for (EventoNotificacion evento : values()) {
            if (evento.id == id) {
                return evento;
            }
        }
        throw new IllegalArgumentException("Evento desconocido: " + id);
    }
}