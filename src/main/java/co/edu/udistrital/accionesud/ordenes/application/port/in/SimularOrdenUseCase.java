package co.edu.udistrital.accionesud.ordenes.application.port.in;

import co.edu.udistrital.accionesud.ordenes.application.dto.SimulacionOrden;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;

import java.util.UUID;

public interface SimularOrdenUseCase {

    SimulacionOrden simular(UUID inversionistaId, String ticker, LadoOrden lado, int cantidad,
                            boolean tieneComisionista);
}
