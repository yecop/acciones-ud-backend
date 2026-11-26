package co.edu.udistrital.accionesud.ordenes.application.port.out;

import co.edu.udistrital.accionesud.ordenes.application.dto.PrecioSimulacion;

public interface PrecioSimulacionPort {

    PrecioSimulacion obtener(String ticker);
}
