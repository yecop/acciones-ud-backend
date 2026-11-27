package co.edu.udistrital.accionesud.ordenes.application.service;

import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.ordenes.application.dto.SimulacionOrden;
import co.edu.udistrital.accionesud.ordenes.application.dto.PrecioSimulacion;
import co.edu.udistrital.accionesud.ordenes.application.port.in.SimularOrdenUseCase;
import co.edu.udistrital.accionesud.ordenes.application.port.out.ParametrosPort;
import co.edu.udistrital.accionesud.ordenes.application.port.out.PrecioSimulacionPort;
import co.edu.udistrital.accionesud.ordenes.application.port.out.PosicionDisponiblePort;
import co.edu.udistrital.accionesud.ordenes.domain.model.ComisionCalculada;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;
import co.edu.udistrital.accionesud.ordenes.domain.model.TipoOrden;
import co.edu.udistrital.accionesud.ordenes.domain.service.CalculadoraComision;
import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Orquesta precio, parámetros y saldo en modo de consulta, sin crear ni reservar órdenes. */
public class SimularOrdenService implements SimularOrdenUseCase {

    private static final String PORCENTAJE = "comision.porcentaje";
    private static final String REPARTO_PLATAFORMA = "comision.reparto.plataforma";

    private final PrecioSimulacionPort precios;
    private final ParametrosPort parametros;
    private final ConsultarSaldoUseCase saldos;
    private final CalculadoraComision calculadora;
    private final PosicionDisponiblePort posiciones;

    public SimularOrdenService(PrecioSimulacionPort precios, ParametrosPort parametros,
                               ConsultarSaldoUseCase saldos, CalculadoraComision calculadora,
                               PosicionDisponiblePort posiciones) {
        this.precios = Objects.requireNonNull(precios);
        this.parametros = Objects.requireNonNull(parametros);
        this.saldos = Objects.requireNonNull(saldos);
        this.calculadora = Objects.requireNonNull(calculadora);
        this.posiciones = Objects.requireNonNull(posiciones);
    }

    @Override
    public SimulacionOrden simular(UUID inversionistaId, String ticker, LadoOrden lado, TipoOrden tipo, int cantidad,
                                   BigDecimal precioLimite, BigDecimal precioStop, boolean tieneComisionista) {
        Objects.requireNonNull(inversionistaId, "El inversionista es obligatorio");
        validar(ticker, lado, tipo, cantidad, precioLimite, precioStop);
        String normalizado = ticker.trim().toUpperCase(Locale.ROOT);
        PrecioSimulacion precio = precios.obtener(normalizado, tipo == TipoOrden.MARKET);
        BigDecimal precioOrden = switch (tipo) {
            case MARKET -> {
                yield Objects.requireNonNull(precio.precio(),
                        "El adaptador debe proporcionar una cotización para órdenes MARKET");
            }
            case LIMIT -> precioLimite;
            case STOP_LOSS, TAKE_PROFIT -> precioStop;
        };
        BigDecimal porcentaje = parametros.obtenerDecimal(PORCENTAJE + "." + precio.mercadoCodigo())
                .or(() -> parametros.obtenerDecimal(PORCENTAJE))
                .orElseThrow(() -> new IllegalStateException("No está configurado el porcentaje de comisión"));
        BigDecimal reparto = parametros.obtenerDecimal(REPARTO_PLATAFORMA)
                .orElseThrow(() -> new IllegalStateException("No está configurado el reparto de comisión"));
        ComisionCalculada comision = calculadora.calcular(precioOrden, cantidad, porcentaje, reparto,
                tieneComisionista, lado);
        Saldo saldo = saldos.consultarSaldo(inversionistaId.toString());
        boolean fondosSuficientes = lado == LadoOrden.VENTA
                ? posiciones.cantidadDisponible(inversionistaId, normalizado) >= cantidad
                : saldo.getDisponible().compareTo(comision.neto()) >= 0;
        return new SimulacionOrden(normalizado, lado, tipo, cantidad, precioOrden, comision,
                fondosSuficientes, precio);
    }

    private static void validar(String ticker, LadoOrden lado, TipoOrden tipo, int cantidad,
                                BigDecimal precioLimite, BigDecimal precioStop) {
        if (ticker == null || ticker.isBlank()) {
            throw new ValidacionException("ticker", "es obligatorio");
        }
        if (ticker.length() > 12) {
            throw new ValidacionException("ticker", "no puede superar 12 caracteres");
        }
        if (lado == null) {
            throw new ValidacionException("lado", "es obligatorio");
        }
        if (tipo == null) {
            throw new ValidacionException("tipo", "es obligatorio");
        }
        if (cantidad < 1) {
            throw new ValidacionException("cantidad", "debe ser al menos 1");
        }
        if (tipo == TipoOrden.LIMIT && (precioLimite == null || precioLimite.signum() <= 0)) {
            throw new ValidacionException("precioLimite", "es obligatorio y debe ser mayor que 0 para LIMIT");
        }
        if ((tipo == TipoOrden.STOP_LOSS || tipo == TipoOrden.TAKE_PROFIT)
                && (precioStop == null || precioStop.signum() <= 0)) {
            throw new ValidacionException("precioStop", "es obligatorio y debe ser mayor que 0 para este tipo");
        }
    }
}
