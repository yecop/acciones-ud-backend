package co.edu.udistrital.accionesud.mercado.domain.port.out;

import co.edu.udistrital.accionesud.mercado.domain.model.Accion;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida con el catálogo de acciones negociables en la plataforma.
 */
public interface AccionCatalogoPort {

    List<Accion> listar();

    Optional<Accion> buscarPorTicker(String ticker);
}
