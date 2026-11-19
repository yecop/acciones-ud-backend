package co.edu.udistrital.accionesud.mercado.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Acción del catálogo de la plataforma (ver migración V7 {@code accion}).
 *
 * @param ticker  símbolo en mayúsculas.
 * @param nombre  nombre de la empresa emisora.
 * @param mercado mercado donde cotiza.
 * @param sector  sector económico.
 * @param moneda  moneda de cotización (ISO 4217).
 */
public record Accion(String ticker, String nombre, CodigoMercado mercado, Sector sector, String moneda) {

    public Accion {
        Objects.requireNonNull(ticker, "El ticker es obligatorio");
        Objects.requireNonNull(nombre, "El nombre es obligatorio");
        Objects.requireNonNull(mercado, "El mercado es obligatorio");
        Objects.requireNonNull(sector, "El sector es obligatorio");
        Objects.requireNonNull(moneda, "La moneda es obligatoria");
        ticker = ticker.trim().toUpperCase(Locale.ROOT);
    }

    /** Las acciones de NYSE y NASDAQ se alimentan desde Alpaca (feed IEX). */
    public boolean esDeEstadosUnidos() {
        return mercado == CodigoMercado.NYSE || mercado == CodigoMercado.NASDAQ;
    }
}
