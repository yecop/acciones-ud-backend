package co.edu.udistrital.accionesud.perfil.domain.model;

public enum TipoOrden {
    MARKET(1, true),
    LIMIT(2, true),
    STOP_LOSS(3, true),
    TAKE_PROFIT(4, true),
    TRAILING_STOP(0, false);

    private final int id;
    private final boolean habilitado;

    TipoOrden(int id, boolean habilitado) {
        this.id = id;
        this.habilitado = habilitado;
    }

    public int id() {
        return id;
    }

    public boolean habilitado() {
        return habilitado;
    }

    public static TipoOrden desdeId(int id) {
        for (TipoOrden tipo : values()) {
            if (tipo.habilitado && tipo.id == id) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de orden desconocido: " + id);
    }
}