package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.out.mercado;

import co.edu.udistrital.accionesud.mercado.application.dto.AccionConCotizacion;
import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarAccionesUseCase;
import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarEstadoMercadoUseCase;
import co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException;
import co.edu.udistrital.accionesud.mercado.domain.model.Advertencia;
import co.edu.udistrital.accionesud.mercado.domain.model.CotizacionVigente;
import co.edu.udistrital.accionesud.mercado.domain.model.EstadoMercadoSnapshot;
import co.edu.udistrital.accionesud.ordenes.application.port.out.PrecioSimulacionPort;
import co.edu.udistrital.accionesud.ordenes.application.dto.PrecioSimulacion;

import java.util.Objects;

/** Lee precio y estado de mercado desde los casos de uso de SCRUM-23/24, nunca llama al proveedor. */
public class PrecioSimulacionAdapter implements PrecioSimulacionPort {

    private final ConsultarAccionesUseCase acciones;
    private final ConsultarEstadoMercadoUseCase mercados;

    public PrecioSimulacionAdapter(ConsultarAccionesUseCase acciones,
                                   ConsultarEstadoMercadoUseCase mercados) {
        this.acciones = Objects.requireNonNull(acciones);
        this.mercados = Objects.requireNonNull(mercados);
    }

    @Override
    public PrecioSimulacion obtener(String ticker) {
        AccionConCotizacion accion = acciones.obtener(ticker);
        if (accion.cotizacion() == null) {
            throw new ProveedorNoDisponibleException("No hay precio en caché para " + accion.accion().ticker(),
                    accion.accion().mercado());
        }
        CotizacionVigente cotizacion = accion.cotizacion();
        EstadoMercadoSnapshot estado = mercados.consultarEstado(accion.accion().mercado());
        Advertencia advertencia = cotizacion.advertencia();
        return new PrecioSimulacion(accion.accion().ticker(), accion.accion().mercado().name(),
                cotizacion.cotizacion().precio(), cotizacion.cotizacion().actualizadoEn(),
                cotizacion.desactualizada(), advertencia == null ? null : advertencia.codigo(),
                advertencia == null ? null : advertencia.mensaje(), estado.abierto(),
                estado.proximaApertura().orElse(null));
    }
}
