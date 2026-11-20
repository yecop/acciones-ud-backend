package co.edu.udistrital.accionesud.perfil.domain.model;

import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException;
import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException.ErrorCampo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Preferencias operativas persistibles de un inversionista.
 * @param tipoVisualizacion estilo para visualizar los activos
 * @param ordenPredeterminado criterio predeterminado de orden del portafolio
 * @param ocultarActivosMenorPeso si se ocultan activos con menor peso
 * @param tipoOrdenPredeterminado tipo de orden predeterminado; debe estar habilitado
 * @param limiteOperacionesDiarias máximo diario, o {@code null} si no hay límite personal
 * @param dobleAutorizacion si las órdenes grandes requieren segunda autorización
 * @param umbralOrdenGrande umbral monetario de autorización, expresado en USD
 * @param reporteMensual si se envía el reporte mensual
 * @param canales canales de notificación; se requiere al menos uno
 * @param eventos eventos notificados; el conjunto puede estar vacío
 */
public record PreferenciasPerfil(
        TipoVisualizacion tipoVisualizacion,
        OrdenPortafolio ordenPredeterminado,
        boolean ocultarActivosMenorPeso,
        TipoOrden tipoOrdenPredeterminado,
        Integer limiteOperacionesDiarias,
        boolean dobleAutorizacion,
        BigDecimal umbralOrdenGrande,
        boolean reporteMensual,
        Set<CanalNotificacion> canales,
        Set<EventoNotificacion> eventos) {

    public PreferenciasPerfil {
        List<ErrorCampo> errores = new ArrayList<>();
        if (tipoVisualizacion == null) {
            errores.add(new ErrorCampo("tipoVisualizacion", "es obligatorio"));
        }
        if (ordenPredeterminado == null) {
            errores.add(new ErrorCampo("ordenPredeterminado", "es obligatorio"));
        }
        if (tipoOrdenPredeterminado == null) {
            errores.add(new ErrorCampo("tipoOrdenPredeterminado", "es obligatorio"));
        } else if (!tipoOrdenPredeterminado.habilitado()) {
            errores.add(new ErrorCampo("tipoOrdenPredeterminado",
                    tipoOrdenPredeterminado.name() + " no está disponible por ahora"));
        }
        if (limiteOperacionesDiarias != null && limiteOperacionesDiarias < 1) {
            errores.add(new ErrorCampo("limiteOperacionesDiarias", "debe ser mayor o igual a 1"));
        }
        if (umbralOrdenGrande != null && umbralOrdenGrande.signum() <= 0) {
            errores.add(new ErrorCampo("umbralOrdenGrande", "debe ser mayor que 0"));
        }
        if (dobleAutorizacion && umbralOrdenGrande == null) {
            errores.add(new ErrorCampo("umbralOrdenGrande",
                    "es obligatorio cuando la doble autorización está activa"));
        }
        if (canales == null || canales.isEmpty()) {
            errores.add(new ErrorCampo("canales", "debe seleccionar al menos un canal"));
        }
        if (eventos == null) {
            errores.add(new ErrorCampo("eventos", "es obligatorio"));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }

        if (umbralOrdenGrande != null) {
            umbralOrdenGrande = umbralOrdenGrande.setScale(2, RoundingMode.HALF_UP);
        }
        canales = EnumSet.copyOf(canales);
        eventos = eventos.isEmpty() ? EnumSet.noneOf(EventoNotificacion.class) : EnumSet.copyOf(eventos);
    }

    /** Valores que se muestran mientras el usuario no ha guardado preferencias. */
    public static PreferenciasPerfil porDefecto() {
        return new PreferenciasPerfil(
                TipoVisualizacion.LISTA,
                OrdenPortafolio.VALOR_TOTAL,
                false,
                TipoOrden.MARKET,
                null,
                false,
                null,
                false,
                EnumSet.of(CanalNotificacion.EMAIL),
                EnumSet.allOf(EventoNotificacion.class));
    }
}
