package co.edu.udistrital.accionesud.mercado.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Sectores económicos del catálogo de acciones (ver migración V6 {@code sector}).
 * El nombre del enum es el valor que viaja en el contrato REST (sin tildes).
 */
public enum Sector {
    TECNOLOGIA,
    FINANZAS,
    SALUD,
    ENERGIA,
    CONSUMO,
    MATERIALES;

    /**
     * Interpreta un sector sin importar mayúsculas ni tildes ("Tecnología" → TECNOLOGIA).
     *
     * @throws IllegalArgumentException si el valor no corresponde a ningún sector.
     */
    public static Sector desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El sector no puede ser vacío");
        }
        String normalizado = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return Sector.valueOf(normalizado);
    }
}
