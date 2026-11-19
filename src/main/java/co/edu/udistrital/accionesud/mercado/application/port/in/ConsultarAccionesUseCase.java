package co.edu.udistrital.accionesud.mercado.application.port.in;

import co.edu.udistrital.accionesud.mercado.application.dto.AccionConCotizacion;
import co.edu.udistrital.accionesud.mercado.application.dto.FiltroAcciones;
import co.edu.udistrital.accionesud.mercado.application.dto.Pagina;
import co.edu.udistrital.accionesud.mercado.domain.model.CotizacionVigente;

/**
 * Casos de uso de lectura de acciones y cotizaciones. Todos leen SOLO de la caché;
 * nunca disparan una llamada al proveedor.
 */
public interface ConsultarAccionesUseCase {

    Pagina<AccionConCotizacion> listar(FiltroAcciones filtro, int pagina, int tamano);

    AccionConCotizacion obtener(String ticker);

    /**
     * @throws co.edu.udistrital.accionesud.mercado.domain.exception.AccionNoEncontradaException
     *         si el ticker no existe.
     * @throws co.edu.udistrital.accionesud.mercado.domain.exception.ProveedorNoDisponibleException
     *         si nunca hubo dato en caché para el ticker.
     */
    CotizacionVigente obtenerCotizacion(String ticker);
}
