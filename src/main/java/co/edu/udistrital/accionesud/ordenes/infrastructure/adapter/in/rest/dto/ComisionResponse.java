package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.ordenes.domain.model.ComisionCalculada;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.util.MoneyFormatter;

public record ComisionResponse(String porcentaje,
                               String valor,
                               String valorPlataforma,
                               String valorComisionista) {

    public static ComisionResponse desde(ComisionCalculada comision) {
        return new ComisionResponse(MoneyFormatter.format(comision.porcentaje()),
                MoneyFormatter.format(comision.valor()), MoneyFormatter.format(comision.valorPlataforma()),
                MoneyFormatter.format(comision.valorComisionista()));
    }
}
