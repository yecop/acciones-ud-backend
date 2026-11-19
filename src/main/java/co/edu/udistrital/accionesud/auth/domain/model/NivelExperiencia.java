package co.edu.udistrital.accionesud.auth.domain.model;

public enum NivelExperiencia {
    PRINCIPIANTE(1), EXPERIMENTADO(2);

    private final int id;

    NivelExperiencia(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}