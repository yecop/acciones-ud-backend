package co.edu.udistrital.accionesud.ordenes.application.service;

import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.ordenes.application.dto.SimulacionOrden;
import co.edu.udistrital.accionesud.ordenes.application.dto.PrecioSimulacion;
import co.edu.udistrital.accionesud.ordenes.application.port.in.SimularOrdenUseCase;
import co.edu.udistrital.accionesud.ordenes.application.port.out.ParametrosPort;
import co.edu.udistrital.accionesud.ordenes.application.port.out.PrecioSimulacionPort;
import co.edu.udistrital.accionesud.ordenes.domain.model.ComisionCalculada;
import co.edu.udistrital.accionesud.ordenes.domain.model.LadoOrden;
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

    public SimularOrdenService(PrecioSimulacionPort precios, ParametrosPort parametros,
                               ConsultarSaldoUseCase saldos, CalculadoraComision calculadora) {
        this.precios = Objects.requireNonNull(precios);
        this.parametros = Objects.requireNonNull(parametros);
        this.saldos = Objects.requireNonNull(saldos);
        this.calculadora = Objects.requireNonNull(calculadora);
    }

    @Override
    public SimulacionOrden simular(UUID inversionistaId, String ticker, LadoOrden lado, int cantidad,
                                   boolean tieneComisionista) {
        Objects.requireNonNull(inversionistaId, "El inversionista es obligatorio");
        validar(ticker, lado, cantidad);
        String normalizado = ticker.trim().toUpperCase(Locale.ROOT);
        PrecioSimulacion precio = precios.obtener(normalizado);
        BigDecimal porcentaje = parametros.obtenerDecimal(PORCENTAJE + "." + precio.mercadoCodigo())
                .or(() -> parametros.obtenerDecimal(PORCENTAJE))
                .orElseThrow(() -> new IllegalStateException("No está configurado el porcentaje de comisión"));
        BigDecimal reparto = parametros.obtenerDecimal(REPARTO_PLATAFORMA)
                .orElseThrow(() -> new IllegalStateException("No está configurado el reparto de comisión"));
        ComisionCalculada comision = calculadora.calcular(precio.precio(), cantidad, porcentaje, reparto,
                tieneComisionista, lado);
        Saldo saldo = saldos.consultarSaldo(inversionistaId.toString());
        boolean fondosSuficientes = lado == LadoOrden.VENTA
                || saldo.getDisponible().compareTo(comision.neto()) >= 0;
        return new SimulacionOrden(normalizado, lado, cantidad, precio.precio(), comision,
                fondosSuficientes, precio);
    }

    private static void validar(String ticker, LadoOrden lado, int cantidad) {
        if (ticker == null || ticker.isBlank()) {
            throw new ValidacionException("ticker", "es obligatorio");
        }
        if (ticker.length() > 12) {
            throw new ValidacionException("ticker", "no puede superar 12 caracteres");
        }
        if (lado == null) {
            throw new ValidacionException("lado", "es obligatorio");
        }
        if (cantidad < 1) {
            throw new ValidacionException("cantidad", "debe ser al menos 1");
        }
    }
}
