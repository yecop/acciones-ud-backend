package co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.perfil.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.perfil.domain.model.EventoNotificacion;
import co.edu.udistrital.accionesud.perfil.domain.model.OrdenPortafolio;
import co.edu.udistrital.accionesud.perfil.domain.model.PreferenciasPerfil;
import co.edu.udistrital.accionesud.perfil.domain.model.TipoOrden;
import co.edu.udistrital.accionesud.perfil.domain.model.TipoVisualizacion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.Set;

public record PreferenciasRequest(
        @NotNull TipoVisualizacion tipoVisualizacion,
        @NotNull OrdenPortafolio ordenPredeterminado,
        @NotNull Boolean ocultarActivosMenorPeso,
        @NotNull TipoOrden tipoOrdenPredeterminado,
        @Min(1) Integer limiteOperacionesDiarias,
        @NotNull Boolean dobleAutorizacion,
        @Pattern(regexp = "^\\d{1,16}(\\.\\d{1,2})?$",
                message = "debe ser un monto con máximo 2 decimales") String umbralOrdenGrande,
        @NotNull Boolean reporteMensual,
        @NotEmpty Set<CanalNotificacion> canales,
        @NotNull Set<EventoNotificacion> eventos) {

    public PreferenciasPerfil aDominio() {
        return new PreferenciasPerfil(
                tipoVisualizacion,
                ordenPredeterminado,
                ocultarActivosMenorPeso,
                tipoOrdenPredeterminado,
                limiteOperacionesDiarias,
                dobleAutorizacion,
                umbralOrdenGrande == null ? null : new BigDecimal(umbralOrdenGrande),
                reporteMensual,
                canales,
                eventos);
    }
}