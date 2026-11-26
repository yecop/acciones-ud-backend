package co.edu.udistrital.accionesud.notificaciones.domain.model;

/** Canales externos de entrega; coinciden con el catálogo {@code canal_notificacion} (V6). */
public enum CanalNotificacion {
    SMS(1), EMAIL(2), WHATSAPP(3);

    private final int id;

    CanalNotificacion(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static CanalNotificacion desdeId(int id) {
        for (CanalNotificacion canal : values()) {
            if (canal.id == id) {
                return canal;
            }
        }
        throw new IllegalArgumentException("Canal desconocido: " + id);
    }
}
