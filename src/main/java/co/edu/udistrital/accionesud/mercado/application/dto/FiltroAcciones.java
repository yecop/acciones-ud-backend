package co.edu.udistrital.accionesud.mercado.application.dto;

import co.edu.udistrital.accionesud.mercado.domain.model.Accion;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Sector;

import java.util.Locale;

/**
 * Filtros opcionales de {@code GET /api/v1/acciones}. Un valor {@code null} no filtra.
 *
 * @param mercado código de mercado.
 * @param sector  sector económico.
 * @param q       texto libre que se busca en el ticker o en el nombre (sin distinguir mayúsculas).
 */
public record FiltroAcciones(CodigoMercado mercado, Sector sector, String q) {

    public static FiltroAcciones sinFiltros() {
        return new FiltroAcciones(null, null, null);
    }

    public boolean cumple(Accion accion) {
        if (mercado != null && accion.mercado() != mercado) {
            return false;
        }
        if (sector != null && accion.sector() != sector) {
            return false;
        }
        if (q != null && !q.isBlank()) {
            String texto = q.trim().toLowerCase(Locale.ROOT);
            return accion.ticker().toLowerCase(Locale.ROOT).contains(texto)
                    || accion.nombre().toLowerCase(Locale.ROOT).contains(texto);
        }
        return true;
    }
}
