package co.edu.udistrital.accionesud.auth.domain.model;

public enum EstadoUsuario {
    ACTIVO(1), PENDIENTE_ALTA(2), OPERACIONES_RESTRINGIDAS(3);

    private final int id;

    EstadoUsuario(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}