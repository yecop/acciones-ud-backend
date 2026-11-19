package co.edu.udistrital.accionesud.mercado.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Cotización leída de la caché junto con su evaluación de antigüedad.
 *
 * @param cotizacion               dato almacenado en caché.
 * @param antiguedadSegundos       segundos transcurridos desde {@code actualizadoEn}.
 * @param umbralAntiguedadSegundos umbral aplicado según el mercado (15 s en EE. UU.).
 * @param desactualizada           {@code true} si el dato supera el umbral o el proveedor falla.
 * @param advertencia              advertencia a mostrar, o {@code null} si el dato está al día.
 */
public record CotizacionVigente(Cotizacion cotizacion,
                                long antiguedadSegundos,
                                long umbralAntiguedadSegundos,
                                boolean desactualizada,
                                Advertencia advertencia) {

    public CotizacionVigente {
        Objects.requireNonNull(cotizacion, "La cotización es obligatoria");
    }

    public Optional<Advertencia> advertenciaOpcional() {
        return Optional.ofNullable(advertencia);
    }
}
