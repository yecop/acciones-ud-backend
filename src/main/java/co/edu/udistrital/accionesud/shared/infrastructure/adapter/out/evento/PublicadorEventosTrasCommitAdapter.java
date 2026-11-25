package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.evento;

import co.edu.udistrital.accionesud.shared.application.port.out.PublicadorEventosPort;
import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * Implementa {@link PublicadorEventosPort} sobre los eventos de Spring (SCRUM-33).
 *
 * <ul>
 *   <li>Con una transacción activa, registra una sincronización y entrega el evento en
 *   {@code afterCommit}. Si hay rollback, {@code afterCommit} no se ejecuta y el evento no sale.</li>
 *   <li>Sin transacción, lo entrega de inmediato.</li>
 *   <li>La entrega es en el mismo hilo y en el orden de publicación. Si un consumidor lanza una
 *   excepción se registra y no afecta al llamador (la operación ya está confirmada).</li>
 * </ul>
 *
 * <p>Los consumidores se suscriben con {@code @EventListener} al tipo de evento.</p>
 */
public class PublicadorEventosTrasCommitAdapter implements PublicadorEventosPort {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventosTrasCommitAdapter.class);

    private final ApplicationEventPublisher publicador;

    public PublicadorEventosTrasCommitAdapter(ApplicationEventPublisher publicador) {
        this.publicador = Objects.requireNonNull(publicador);
    }

    @Override
    public void publicar(EventoDominio evento) {
        Objects.requireNonNull(evento, "El evento es obligatorio");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    entregar(evento);
                }
            });
            log.debug("Evento {} ({}) en espera del commit", evento.tipo(), evento.metadatos().id());
        } else {
            entregar(evento);
        }
    }

    private void entregar(EventoDominio evento) {
        try {
            publicador.publishEvent(evento);
        } catch (RuntimeException e) {
            log.error("Un consumidor falló al procesar el evento {} ({}, traceId={})", evento.tipo(),
                    evento.metadatos().id(), evento.metadatos().traceId(), e);
        }
    }
}
