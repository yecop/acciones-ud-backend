package co.edu.udistrital.accionesud.shared.domain.broker;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Contratos del puerto de broker para órdenes (SCRUM-33, lo implementa SCRUM-43 con Alpaca).
 */
public final class OrdenBroker {

    private OrdenBroker() {
    }

    public enum Lado { COMPRA, VENTA }

    /** Coincide con el catálogo {@code tipo_orden}. */
    public enum Tipo { MARKET, LIMIT, STOP_LOSS, TAKE_PROFIT }

    /** Estado de la orden del lado del broker. */
    public enum Estado {
        ACEPTADA,
        PARCIALMENTE_EJECUTADA,
        EJECUTADA,
        CANCELADA,
        RECHAZADA,
        EXPIRADA;

        public boolean esFinal() {
            return this == EJECUTADA || this == CANCELADA || this == RECHAZADA || this == EXPIRADA;
        }
    }

    /**
     * Orden a enviar al broker.
     *
     * @param idCliente identificador propio de la orden (p. ej. el UUID de la orden interna). El
     *                  broker lo usa como clave de idempotencia: reenviar el mismo idCliente no crea
     *                  otra orden.
     */
    public record Solicitud(String idCliente, String ticker, Lado lado, int cantidad, Tipo tipo,
                            BigDecimal precioLimite, BigDecimal precioStop) {
        public Solicitud {
            if (idCliente == null || idCliente.isBlank()) {
                throw new IllegalArgumentException("El idCliente es obligatorio (idempotencia)");
            }
            if (ticker == null || ticker.isBlank()) {
                throw new IllegalArgumentException("El ticker es obligatorio");
            }
            Objects.requireNonNull(lado, "El lado es obligatorio");
            Objects.requireNonNull(tipo, "El tipo es obligatorio");
            if (cantidad < 1) {
                throw new IllegalArgumentException("La cantidad debe ser al menos 1");
            }
            if (tipo == Tipo.LIMIT && precioLimite == null) {
                throw new IllegalArgumentException("Una orden LIMIT requiere precio límite");
            }
            if ((tipo == Tipo.STOP_LOSS || tipo == Tipo.TAKE_PROFIT) && precioStop == null) {
                throw new IllegalArgumentException("Una orden " + tipo + " requiere precio stop");
            }
        }
    }

    /** Estado de la orden según el broker. */
    public record Respuesta(String idExterno, String idCliente, Estado estado, int cantidadEjecutada,
                            BigDecimal precioPromedio, String motivoRechazo) {
        public Respuesta {
            Objects.requireNonNull(idCliente, "El idCliente es obligatorio");
            Objects.requireNonNull(estado, "El estado es obligatorio");
            if (cantidadEjecutada < 0) {
                throw new IllegalArgumentException("La cantidad ejecutada no puede ser negativa");
            }
        }
    }
}
