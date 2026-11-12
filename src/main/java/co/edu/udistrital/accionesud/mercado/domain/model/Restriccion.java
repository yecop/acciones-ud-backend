package co.edu.udistrital.accionesud.mercado.domain.model;

import java.util.Objects;

/**
 * Restricción vigente que afecta la operación del mercado (p. ej. feriado, cierre anticipado).
 */
public final class Restriccion {

    private final String codigo;
    private final String descripcion;

    public Restriccion(String codigo, String descripcion) {
        this.codigo = Objects.requireNonNull(codigo, "El código de restricción es obligatorio");
        this.descripcion = Objects.requireNonNull(descripcion, "La descripción es obligatoria");
    }

    public static Restriccion de(String codigo, String descripcion) {
        return new Restriccion(codigo, descripcion);
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }
}
