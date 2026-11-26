package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoRecarga;

/**
 * Respuesta de {@code POST /cuenta/recargas}: el depósito {@code PENDIENTE} y la página de pago a
 * la que el frontend redirige. {@code urlPago} es {@code null} si la sesión ya no está abierta.
 */
public record RecargaResponse(MovimientoFondosResponse movimiento, String urlPago) {

    public static RecargaResponse desde(ResultadoRecarga resultado) {
        return new RecargaResponse(MovimientoFondosResponse.desde(resultado.movimiento()), resultado.urlPago());
    }
}
