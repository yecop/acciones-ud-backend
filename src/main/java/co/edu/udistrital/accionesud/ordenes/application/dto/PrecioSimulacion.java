package co.edu.udistrital.accionesud.ordenes.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Datos de mercado de solo lectura necesarios para presentar una simulación. */
public record PrecioSimulacion(String ticker,
                               String mercadoCodigo,
                               BigDecimal precio,
                               Instant actualizadoEn,
                               boolean desactualizada,
                               String advertenciaCodigo,
                               String advertenciaMensaje,
                               boolean mercadoAbierto,
                               Instant proximaApertura) {

    public PrecioSimulacion {
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(mercadoCodigo);
        Objects.requireNonNull(precio);
        Objects.requireNonNull(actualizadoEn);
        if (precio.signum() <= 0) {
            throw new IllegalArgumentException("La cotización debe ser positiva");
        }
    }
}
