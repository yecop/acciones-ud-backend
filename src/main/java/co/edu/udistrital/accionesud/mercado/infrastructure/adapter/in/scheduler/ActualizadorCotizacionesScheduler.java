package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.scheduler;

import co.edu.udistrital.accionesud.mercado.application.port.in.ActualizarCotizacionesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Adaptador de entrada programado: el ÚNICO disparador de llamadas a proveedores (SCRUM-24).
 *
 * <ul>
 *   <li>EE. UU.: cada {@code accionesud.mercados.cotizaciones.intervalo-ms} (5 s ⇒ 12
 *   llamadas por minuto a Alpaca, independiente de cuántos usuarios consulten).</li>
 *   <li>Internacional: cada {@code ...cotizaciones.internacional.intervalo-ms} (60 s por
 *   defecto, por los límites de los planes gratuitos).</li>
 * </ul>
 * El planificador de Spring usa un solo hilo, así que dos ciclos nunca se solapan.
 */
@Component
@ConditionalOnProperty(prefix = "accionesud.mercados.cotizaciones", name = "actualizador-habilitado",
        havingValue = "true", matchIfMissing = true)
public class ActualizadorCotizacionesScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActualizadorCotizacionesScheduler.class);

    private final ActualizarCotizacionesUseCase actualizar;

    public ActualizadorCotizacionesScheduler(ActualizarCotizacionesUseCase actualizar) {
        this.actualizar = actualizar;
    }

    @Scheduled(fixedRateString = "${accionesud.mercados.cotizaciones.intervalo-ms:5000}",
            initialDelayString = "${accionesud.mercados.cotizaciones.retardo-inicial-ms:2000}")
    public void estadosUnidos() {
        ejecutar("EE. UU.", actualizar::actualizarEstadosUnidos);
    }

    @Scheduled(fixedRateString = "${accionesud.mercados.cotizaciones.internacional.intervalo-ms:60000}",
            initialDelayString = "${accionesud.mercados.cotizaciones.retardo-inicial-ms:2000}")
    public void internacionales() {
        ejecutar("internacional", actualizar::actualizarInternacionales);
    }

    private void ejecutar(String grupo, Supplier<?> ciclo) {
        try {
            log.debug("Ciclo de cotizaciones {}: {}", grupo, ciclo.get());
        } catch (RuntimeException e) {
            // Nunca dejar que una excepción detenga el planificador.
            log.error("Error inesperado en el ciclo de cotizaciones {}", grupo, e);
        }
    }
}
