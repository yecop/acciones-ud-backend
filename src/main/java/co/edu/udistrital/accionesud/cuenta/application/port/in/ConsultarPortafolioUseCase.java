package co.edu.udistrital.accionesud.cuenta.application.port.in;

import co.edu.udistrital.accionesud.cuenta.application.dto.PosicionValorada;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResumenPortafolio;

import java.util.List;

/**
 * Puerto de entrada: consultar el portafolio del usuario, tanto su resumen de
 * valor total como el detalle de posiciones valoradas con precios de la cache.
 */
public interface ConsultarPortafolioUseCase {

    /**
     * Devuelve el resumen del portafolio (valor total, costo total y P&amp;L).
     * Para un usuario nuevo el resumen refleja un portafolio vacio (ceros).
     *
     * @param usuarioId identificador del usuario.
     * @return resumen del portafolio.
     */
    ResumenPortafolio consultarResumen(String usuarioId);

    /**
     * Devuelve el detalle de cada posicion ya valorada: activo, cantidad,
     * precio de compra, valor actual, ganancia/perdida y porcentaje.
     * Para un usuario nuevo retorna una lista vacia.
     *
     * @param usuarioId identificador del usuario.
     * @return lista de posiciones valoradas.
     */
    List<PosicionValorada> consultarPosiciones(String usuarioId);
}
