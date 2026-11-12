package co.edu.udistrital.accionesud.mercado.domain.port.out;

import java.time.Instant;

/**
 * Puerto de salida para obtener la hora del sistema.
 *
 * <p>Su objetivo es aislar la lógica de tiempo del reloj real de la máquina,
 * permitiendo inyectar un instante controlado durante las pruebas.</p>
 */
public interface RelojPort {

    /**
     * @return el instante actual según la fuente de tiempo configurada.
     */
    Instant ahora();
}
