package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.out.parametros;

import co.edu.udistrital.accionesud.ordenes.application.port.out.ParametrosPort;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Valores semilla usados cuando el perfil no conecta una base de datos, como en pruebas REST. */
public class ParametrosSistemaMemoriaAdapter implements ParametrosPort {

    private final Map<String, BigDecimal> valores = Map.of(
            "comision.porcentaje", new BigDecimal("2.00"),
            "comision.reparto.plataforma", new BigDecimal("60.00"),
            "comision.reparto.comisionista", new BigDecimal("40.00"));

    @Override
    public Optional<BigDecimal> obtenerDecimal(String clave) {
        return Optional.ofNullable(valores.get(clave));
    }
}
