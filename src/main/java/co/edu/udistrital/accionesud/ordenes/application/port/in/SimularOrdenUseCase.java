package co.edu.udistrital.accionesud.ordenes.application.port.in;

import co.edu.udistrital.accionesud.ordenes.application.dto.SimulacionOrden;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;
import co.edu.udistrital.accionesud.ordenes.domain.model.TipoOrden;

import java.math.BigDecimal;
import java.util.UUID;

public interface SimularOrdenUseCase {

    SimulacionOrden simular(UUID inversionistaId, String ticker, LadoOrden lado, TipoOrden tipo, int cantidad,
                            BigDecimal precioLimite, BigDecimal precioStop, boolean tieneComisionista);
}
