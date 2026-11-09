package co.edu.udistrital.accionesud.mercado.domain.port.out;

import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Mercado;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida que expone el catálogo de mercados configurados
 * (horarios, zonas horarias y feriados).
 */
public interface MercadoCatalogoPort {

    List<Mercado> listar();

    Optional<Mercado> buscarPorCodigo(CodigoMercado codigo);
}
