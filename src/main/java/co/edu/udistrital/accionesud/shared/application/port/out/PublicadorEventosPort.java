package co.edu.udistrital.accionesud.shared.application.port.out;

import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;

/**
 * Publica eventos de dominio hacia otros módulos (SCRUM-33).
 *
 * <p>Contrato:</p>
 * <ul>
 *   <li>Dentro de una transacción ({@link TransaccionPort}), el evento se entrega a los
 *   consumidores <b>solo después del commit</b>.</li>
 *   <li>Si la transacción hace rollback, el evento <b>no</b> se entrega.</li>
 *   <li>Fuera de una transacción, se entrega de inmediato.</li>
 *   <li>Un consumidor que falla no deshace la operación ya confirmada; el fallo se registra.</li>
 * </ul>
 */
public interface PublicadorEventosPort {

    void publicar(EventoDominio evento);
}
