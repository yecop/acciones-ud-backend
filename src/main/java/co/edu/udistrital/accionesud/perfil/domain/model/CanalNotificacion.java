package co.edu.udistrital.accionesud.perfil.domain.model;

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