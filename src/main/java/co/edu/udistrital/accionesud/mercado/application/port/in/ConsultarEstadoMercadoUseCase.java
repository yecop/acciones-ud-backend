package co.edu.udistrital.accionesud.mercado.application.port.in;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.EstadoMercadoSnapshot;

/**
 * Caso de uso: consultar el estado actual de un mercado (abierto/cerrado,
 * próxima apertura, próximo cierre y restricciones vigentes).
 */
public interface ConsultarEstadoMercadoUseCase {

    EstadoMercadoSnapshot consultarEstado(CodigoMercado codigo);
}
