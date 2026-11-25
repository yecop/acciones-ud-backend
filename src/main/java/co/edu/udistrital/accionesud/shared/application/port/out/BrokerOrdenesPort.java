package co.edu.udistrital.accionesud.shared.application.port.out;

import co.edu.udistrital.accionesud.shared.domain.broker.OrdenBroker;
import co.edu.udistrital.accionesud.shared.domain.exception.BrokerOrdenesNoDisponibleException;

import java.util.Optional;

/**
 * Puerto hacia el broker que ejecuta las órdenes (SCRUM-33). Lo implementa SCRUM-43 con Alpaca
 * sandbox; mientras tanto existe un adaptador simulado.
 *
 * <p>Idempotencia: {@link #enviar} con un {@code idCliente} ya enviado devuelve la orden
 * existente y no crea otra, para que los reintentos sean seguros.</p>
 */
public interface BrokerOrdenesPort {

    /**
     * @throws BrokerOrdenesNoDisponibleException si el broker no responde o falla.
     */
    OrdenBroker.Respuesta enviar(OrdenBroker.Solicitud solicitud);

    /** Estado actual de la orden enviada con ese idCliente, si el broker la conoce. */
    Optional<OrdenBroker.Respuesta> consultar(String idCliente);

    /**
     * Solicita cancelar la orden. Devuelve el estado resultante (una orden ya ejecutada no se
     * cancela y se devuelve tal cual).
     *
     * @throws BrokerOrdenesNoDisponibleException si el broker no responde o falla.
     */
    OrdenBroker.Respuesta cancelar(String idCliente);
}
