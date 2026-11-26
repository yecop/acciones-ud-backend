package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoMovimiento;

/** Respuesta de un depósito o retiro: el movimiento y el saldo de la cuenta después de aplicarlo. */
public record OperacionFondosResponse(MovimientoFondosResponse movimiento, SaldoResponse saldo) {

    public static OperacionFondosResponse desde(ResultadoMovimiento resultado) {
        return new OperacionFondosResponse(MovimientoFondosResponse.desde(resultado.movimiento()),
                SaldoResponse.desde(resultado.saldo()));
    }
}
