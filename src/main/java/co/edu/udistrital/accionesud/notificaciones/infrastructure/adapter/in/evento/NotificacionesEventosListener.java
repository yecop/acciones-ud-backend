package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.evento;

import co.edu.udistrital.accionesud.notificaciones.application.port.in.ProcesarEventoUseCase;
import co.edu.udistrital.accionesud.notificaciones.application.port.in.ReintentarEnviosUseCase;
import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * Entrada del módulo (SCRUM-51): consume los eventos de dominio que publica
 * {@code PublicadorEventosPort} después del commit y reintenta periódicamente los envíos pendientes.
 */
public class NotificacionesEventosListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesEventosListener.class);

    private final ProcesarEventoUseCase procesar;
    private final ReintentarEnviosUseCase reintentar;
    private final boolean reintentoHabilitado;

    public NotificacionesEventosListener(ProcesarEventoUseCase procesar, ReintentarEnviosUseCase reintentar,
                                         boolean reintentoHabilitado) {
        this.procesar = Objects.requireNonNull(procesar);
        this.reintentar = Objects.requireNonNull(reintentar);
        this.reintentoHabilitado = reintentoHabilitado;
    }

    @EventListener
    public void alOcurrir(EventoDominio evento) {
        int creadas = procesar.procesar(evento);
        if (creadas > 0) {
            log.debug("Evento {} ({}): {} notificaciones", evento.tipo(), evento.metadatos().id(), creadas);
        }
    }

    @Scheduled(fixedDelayString = "${accionesud.notificaciones.reintento.intervalo-ms:30000}",
            initialDelayString = "${accionesud.notificaciones.reintento.intervalo-ms:30000}")
    public void reintentarPendientes() {
        if (!reintentoHabilitado) {
            return;
        }
        try {
            int intentados = reintentar.reintentarPendientes();
            if (intentados > 0) {
                log.info("Reintento de notificaciones: {} envíos intentados", intentados);
            }
        } catch (RuntimeException e) {
            log.error("Falló el ciclo de reintento de notificaciones", e);
        }
    }
}
