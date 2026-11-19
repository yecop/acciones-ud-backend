package co.edu.udistrital.accionesud.mercado.domain.port.out;

import co.edu.udistrital.accionesud.mercado.domain.model.EstadisticasCuota;

/**
 * Puerto de salida del limitador de cuota hacia el proveedor de datos (SCRUM-24).
 *
 * <p>Debe invocarse antes de CADA llamada al proveedor. El tope de diseño es un
 * porcentaje (60 % por defecto) del límite del plan del proveedor.</p>
 */
public interface LimitadorCuotaPort {

    /**
     * Registra el intento de llamada.
     *
     * @return {@code true} si la llamada está permitida; {@code false} si superaría el tope
     *         (en cuyo caso el adaptador emite una alarma).
     */
    boolean intentarConsumir();

    EstadisticasCuota estadisticas();
}
