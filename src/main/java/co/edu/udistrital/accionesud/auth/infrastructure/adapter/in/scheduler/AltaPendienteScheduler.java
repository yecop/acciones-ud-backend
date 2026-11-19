package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.scheduler;

import co.edu.udistrital.accionesud.auth.application.port.in.ReintentarAltasPendientesUseCase;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.trace.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AltaPendienteScheduler {

    private static final Logger log = LoggerFactory.getLogger(AltaPendienteScheduler.class);

    private final ReintentarAltasPendientesUseCase reintentar;
    private final int maximoPorCiclo;

    public AltaPendienteScheduler(ReintentarAltasPendientesUseCase reintentar, int maximoPorCiclo) {
        this.reintentar = reintentar;
        this.maximoPorCiclo = maximoPorCiclo;
    }

    @Scheduled(fixedDelayString = "${accionesud.auth.reintento.intervalo-segundos:60}",
            initialDelayString = "${accionesud.auth.reintento.intervalo-segundos:60}",
            timeUnit = TimeUnit.SECONDS)
    public void ejecutar() {
        MDC.put(TraceIdFilter.MDC_KEY, UUID.randomUUID().toString());
        try {
            int completadas = reintentar.reintentar(maximoPorCiclo);
            if (completadas > 0) {
                log.info("Altas pendientes completadas: {}", completadas);
            }
        } catch (RuntimeException e) {
            log.error("Falló el reintento de altas pendientes", e);
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }
}