package co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.perfil.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.perfil.domain.model.EventoNotificacion;
import co.edu.udistrital.accionesud.perfil.domain.model.OrdenPortafolio;
import co.edu.udistrital.accionesud.perfil.domain.model.PreferenciasPerfil;
import co.edu.udistrital.accionesud.perfil.domain.model.TipoOrden;
import co.edu.udistrital.accionesud.perfil.domain.model.TipoVisualizacion;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PreferenciasResponse(
        TipoVisualizacion tipoVisualizacion,
        OrdenPortafolio ordenPredeterminado,
        boolean ocultarActivosMenorPeso,
        TipoOrden tipoOrdenPredeterminado,
        Integer limiteOperacionesDiarias,
        boolean dobleAutorizacion,
        String umbralOrdenGrande,
        boolean reporteMensual,
        List<CanalNotificacion> canales,
        List<EventoNotificacion> eventos,
        List<TipoOrdenOpcion> tiposOrdenDisponibles) {

    public record TipoOrdenOpcion(String codigo, boolean habilitado) {
    }

    public static PreferenciasResponse desde(PreferenciasPerfil p) {
        String umbral = p.umbralOrdenGrande() == null
                ? null
                : p.umbralOrdenGrande().setScale(2, RoundingMode.HALF_UP).toPlainString();
        List<TipoOrdenOpcion> tipos = Arrays.stream(TipoOrden.values())
                .map(tipo -> new TipoOrdenOpcion(tipo.name(), tipo.habilitado()))
                .toList();
        return new PreferenciasResponse(
                p.tipoVisualizacion(),
                p.ordenPredeterminado(),
                p.ocultarActivosMenorPeso(),
                p.tipoOrdenPredeterminado(),
                p.limiteOperacionesDiarias(),
                p.dobleAutorizacion(),
                umbral,
                p.reporteMensual(),
                p.canales().stream().sorted().toList(),
                p.eventos().stream().sorted().toList(),
                tipos);
    }
}