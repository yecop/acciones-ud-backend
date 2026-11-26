package co.edu.udistrital.accionesud.ordenes.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Desglose monetario calculado para una simulación, sin persistir una orden. */
public record ComisionCalculada(BigDecimal totalTransaccion,
                                BigDecimal porcentaje,
                                BigDecimal valor,
                                BigDecimal valorPlataforma,
                                BigDecimal valorComisionista,
                                BigDecimal neto) {

    public ComisionCalculada {
        Objects.requireNonNull(totalTransaccion);
        Objects.requireNonNull(porcentaje);
        Objects.requireNonNull(valor);
        Objects.requireNonNull(valorPlataforma);
        Objects.requireNonNull(valorComisionista);
        Objects.requireNonNull(neto);
    }
}
