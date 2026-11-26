package co.edu.udistrital.accionesud.ordenes.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/** Lectura de parámetros decimales de negocio; la infraestructura gestiona la caché. */
public interface ParametrosPort {

    Optional<BigDecimal> obtenerDecimal(String clave);
}
