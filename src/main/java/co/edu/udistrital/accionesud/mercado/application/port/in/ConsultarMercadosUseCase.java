package co.edu.udistrital.accionesud.mercado.application.port.in;

import co.edu.udistrital.accionesud.mercado.domain.model.Mercado;

import java.util.List;

/**
 * Caso de uso: listar los mercados soportados con sus horarios y zonas horarias.
 */
public interface ConsultarMercadosUseCase {

    List<Mercado> listarMercados();
}
