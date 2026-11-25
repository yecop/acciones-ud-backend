package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.broker;

import co.edu.udistrital.accionesud.shared.application.port.out.BrokerOrdenesPort;
import co.edu.udistrital.accionesud.shared.domain.broker.OrdenBroker;
import co.edu.udistrital.accionesud.shared.domain.exception.BrokerOrdenesNoDisponibleException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Broker simulado en memoria (SCRUM-33). Es el adaptador por defecto mientras SCRUM-43 no conecte
 * Alpaca ({@code accionesud.integraciones.broker=simulado}) y sirve de fake en pruebas.
 *
 * <ul>
 *   <li>Acepta las órdenes y las deja en {@code ACEPTADA}.</li>
 *   <li>Idempotente por {@code idCliente}: reenviar la misma orden devuelve la existente.</li>
 *   <li>Permite simular caídas ({@link #simularCaida}) y fijar estados ({@link #fijarEstado}).</li>
 * </ul>
 */
public class BrokerOrdenesSimuladoAdapter implements BrokerOrdenesPort {

    private final Map<String, OrdenBroker.Respuesta> ordenes = new ConcurrentHashMap<>();
    private final AtomicBoolean caido = new AtomicBoolean(false);
    private final AtomicInteger envios = new AtomicInteger();

    @Override
    public OrdenBroker.Respuesta enviar(OrdenBroker.Solicitud solicitud) {
        verificarDisponible();
        envios.incrementAndGet();
        return ordenes.computeIfAbsent(solicitud.idCliente(), id -> new OrdenBroker.Respuesta(
                "sim-" + UUID.randomUUID(), id, OrdenBroker.Estado.ACEPTADA, 0, null, null));
    }

    @Override
    public Optional<OrdenBroker.Respuesta> consultar(String idCliente) {
        verificarDisponible();
        return Optional.ofNullable(ordenes.get(idCliente));
    }

    @Override
    public OrdenBroker.Respuesta cancelar(String idCliente) {
        verificarDisponible();
        OrdenBroker.Respuesta actual = ordenes.get(idCliente);
        if (actual == null) {
            throw new IllegalArgumentException("El broker no conoce la orden " + idCliente);
        }
        if (actual.estado().esFinal()) {
            return actual;
        }
        OrdenBroker.Respuesta cancelada = new OrdenBroker.Respuesta(actual.idExterno(), idCliente,
                OrdenBroker.Estado.CANCELADA, actual.cantidadEjecutada(), actual.precioPromedio(), null);
        ordenes.put(idCliente, cancelada);
        return cancelada;
    }

    // ------------------------------------------------------------------ controles de prueba

    /** Hace que todas las llamadas lancen {@link BrokerOrdenesNoDisponibleException}. */
    public void simularCaida(boolean caido) {
        this.caido.set(caido);
    }

    /** Fija el estado de una orden ya enviada (p. ej. EJECUTADA) para probar la sincronización. */
    public void fijarEstado(String idCliente, OrdenBroker.Estado estado, int cantidadEjecutada,
                            java.math.BigDecimal precioPromedio) {
        OrdenBroker.Respuesta actual = ordenes.get(idCliente);
        if (actual == null) {
            throw new IllegalArgumentException("El broker no conoce la orden " + idCliente);
        }
        ordenes.put(idCliente, new OrdenBroker.Respuesta(actual.idExterno(), idCliente, estado,
                cantidadEjecutada, precioPromedio, null));
    }

    /** Llamadas a {@link #enviar} que llegaron al broker (incluye reenvíos idempotentes). */
    public int envios() {
        return envios.get();
    }

    /** Órdenes distintas que existen en el broker. */
    public int ordenesRegistradas() {
        return ordenes.size();
    }

    private void verificarDisponible() {
        if (caido.get()) {
            throw new BrokerOrdenesNoDisponibleException("Broker simulado: no disponible");
        }
    }
}
