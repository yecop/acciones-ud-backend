package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.util.MoneyFormatter;
import co.edu.udistrital.accionesud.ordenes.application.dto.SimulacionOrden;

import java.math.RoundingMode;
import java.time.Instant;

/** Contrato REST con importes monetarios como texto de dos decimales. */
public record SimulacionOrdenResponse(String ticker,
                                     String lado,
                                     int cantidad,
                                     String precioEstimado,
                                     String totalTransaccion,
                                     ComisionResponse comision,
                                     String totalADescontar,
                                     boolean fondosSuficientes,
                                     boolean mercadoAbierto,
                                     Instant proximaApertura,
                                     Instant actualizadoEn,
                                     boolean desactualizada,
                                     AdvertenciaSimulacionResponse advertencia) {

    public static SimulacionOrdenResponse desde(SimulacionOrden simulacion) {
        var comisionCalculada = simulacion.comision();
        var precio = simulacion.precioMercado();
        AdvertenciaSimulacionResponse advertencia = precio.advertenciaCodigo() == null ? null
                : new AdvertenciaSimulacionResponse(precio.advertenciaCodigo(), precio.advertenciaMensaje());
        return new SimulacionOrdenResponse(simulacion.ticker(), simulacion.lado().name(), simulacion.cantidad(),
                simulacion.precioEstimado().setScale(4, RoundingMode.HALF_UP).toPlainString(),
                MoneyFormatter.format(comisionCalculada.totalTransaccion()),
                ComisionResponse.desde(comisionCalculada), MoneyFormatter.format(comisionCalculada.neto()),
                simulacion.fondosSuficientes(), precio.mercadoAbierto(), precio.proximaApertura(),
                precio.actualizadoEn(), precio.desactualizada(), advertencia);
    }
}
