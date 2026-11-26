package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto;

import java.math.BigDecimal;

/**
 * Cuerpo de {@code POST /cuenta/depositos} y {@code POST /cuenta/retiros}. El monto acepta número
 * o texto ({@code 500} o {@code "500.00"}); las reglas (positivo, 2 decimales, tope) las valida el
 * caso de uso.
 */
public record MovimientoFondosRequest(BigDecimal monto) {
}
