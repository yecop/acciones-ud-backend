package co.edu.udistrital.accionesud.ordenes.application.dto;

import co.edu.udistrital.accionesud.ordenes.domain.model.ComisionCalculada;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;
import co.edu.udistrital.accionesud.ordenes.domain.model.TipoOrden;

import java.math.BigDecimal;
import java.util.Objects;

/** Resultado de solo lectura de la simulación de una orden. */
public record SimulacionOrden(String ticker,
                              LadoOrden lado,
                              TipoOrden tipo,
                              int cantidad,
                              BigDecimal precioEstimado,
                              ComisionCalculada comision,
                              boolean fondosSuficientes,
                              PrecioSimulacion precioMercado) {

    public SimulacionOrden {
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(lado);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(precioEstimado);
        Objects.requireNonNull(comision);
        Objects.requireNonNull(precioMercado);
    }
}
