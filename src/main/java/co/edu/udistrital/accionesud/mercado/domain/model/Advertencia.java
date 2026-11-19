package co.edu.udistrital.accionesud.mercado.domain.model;

import java.util.Objects;

/**
 * Advertencia que acompaña a un dato servido desde la caché.
 *
 * @param codigo  código estable para que el frontend decida (no el texto).
 * @param mensaje texto informativo para la persona usuaria.
 */
public record Advertencia(String codigo, String mensaje) {

    /** El dato supera el umbral de antigüedad permitido. */
    public static final String DATO_DESACTUALIZADO = "DATO_DESACTUALIZADO";
    /** El proveedor falla y se sirve el último dato disponible en caché. */
    public static final String PROVEEDOR_NO_DISPONIBLE = "PROVEEDOR_NO_DISPONIBLE";
    /** Aún no hay cotización en caché para la acción. */
    public static final String SIN_COTIZACION = "SIN_COTIZACION";

    public Advertencia {
        Objects.requireNonNull(codigo, "El código es obligatorio");
        Objects.requireNonNull(mensaje, "El mensaje es obligatorio");
    }
}
