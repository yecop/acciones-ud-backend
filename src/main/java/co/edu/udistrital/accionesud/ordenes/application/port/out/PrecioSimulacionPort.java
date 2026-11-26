package co.edu.udistrital.accionesud.ordenes.application.port.out;

import co.edu.udistrital.accionesud.ordenes.application.dto.PrecioSimulacion;

public interface PrecioSimulacionPort {

    /**
     * Consulta la cotización y el contexto de mercado desde la caché.
     * Si {@code cotizacionObligatoria} es verdadero y no hay cotización, el adaptador informa
     * que el proveedor no está disponible.
     */
    PrecioSimulacion obtener(String ticker, boolean cotizacionObligatoria);
}
