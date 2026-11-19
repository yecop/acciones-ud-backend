package co.edu.udistrital.accionesud.mercado.application.dto;

import co.edu.udistrital.accionesud.mercado.domain.model.Accion;
import co.edu.udistrital.accionesud.mercado.domain.model.Advertencia;
import co.edu.udistrital.accionesud.mercado.domain.model.CotizacionVigente;

import java.util.Objects;

/**
 * Acción del catálogo con su última cotización en caché (si existe).
 *
 * @param accion      datos del catálogo.
 * @param cotizacion  cotización evaluada, o {@code null} si aún no hay dato en caché.
 * @param advertencia advertencia cuando no hay cotización ({@code SIN_COTIZACION}); si hay
 *                    cotización, su advertencia viaja dentro de {@code cotizacion}.
 */
public record AccionConCotizacion(Accion accion, CotizacionVigente cotizacion, Advertencia advertencia) {

    public AccionConCotizacion {
        Objects.requireNonNull(accion, "La acción es obligatoria");
    }
}
