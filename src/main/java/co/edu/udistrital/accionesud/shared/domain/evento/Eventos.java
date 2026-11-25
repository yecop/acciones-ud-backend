package co.edu.udistrital.accionesud.shared.domain.evento;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Catálogo de eventos de dominio compartidos entre módulos (SCRUM-33).
 *
 * <p>Son contratos: los publica el módulo dueño del hecho y los consumen otros (p. ej.
 * notificaciones en SCRUM-51). Los estados viajan como texto para no acoplar a los
 * consumidores con los enums internos de cada módulo.</p>
 */
public final class Eventos {

    private Eventos() {
    }

    // ------------------------------------------------------------------ trading

    /** Se creó una orden (SCRUM-38). */
    public record OrdenCreada(MetadatosEvento metadatos, UUID ordenId, UUID cuentaId, String ticker,
                              String lado, int cantidad, String tipoOrden) implements EventoDominio {
        public OrdenCreada {
            Objects.requireNonNull(metadatos);
            Objects.requireNonNull(ordenId);
            Objects.requireNonNull(cuentaId);
        }
    }

    /** La orden cambió de estado (SCRUM-39, 41, 43). */
    public record OrdenEstadoCambiado(MetadatosEvento metadatos, UUID ordenId, UUID cuentaId,
                                      String estadoAnterior, String estadoNuevo, String motivo)
            implements EventoDominio {
        public OrdenEstadoCambiado {
            Objects.requireNonNull(metadatos);
            Objects.requireNonNull(ordenId);
            Objects.requireNonNull(estadoNuevo);
        }
    }

    /** El broker ejecutó total o parcialmente la orden (SCRUM-43, 44). */
    public record OrdenEjecutada(MetadatosEvento metadatos, UUID ordenId, UUID cuentaId, String ticker,
                                 int cantidadEjecutada, BigDecimal precioEjecucion, boolean completa)
            implements EventoDominio {
        public OrdenEjecutada {
            Objects.requireNonNull(metadatos);
            Objects.requireNonNull(ordenId);
            Objects.requireNonNull(precioEjecucion);
        }
    }

    // ------------------------------------------------------------------ fondos

    /** Depósito o retiro aplicado a la cuenta (SCRUM-46). */
    public record MovimientoFondosRegistrado(MetadatosEvento metadatos, UUID cuentaId, String tipo,
                                             BigDecimal monto, BigDecimal saldoDisponible)
            implements EventoDominio {
        public MovimientoFondosRegistrado {
            Objects.requireNonNull(metadatos);
            Objects.requireNonNull(cuentaId);
            Objects.requireNonNull(monto);
        }
    }

    // ------------------------------------------------------------------ premium

    /** Cambió el plan o el estado de la suscripción (SCRUM-54). */
    public record SuscripcionActualizada(MetadatosEvento metadatos, UUID usuarioId, String plan,
                                         String estado) implements EventoDominio {
        public SuscripcionActualizada {
            Objects.requireNonNull(metadatos);
            Objects.requireNonNull(usuarioId);
        }
    }

    /** Se cumplió la condición de una alerta de precio (SCRUM-55). */
    public record AlertaPrecioDisparada(MetadatosEvento metadatos, UUID usuarioId, UUID alertaId,
                                        String ticker, String condicion, BigDecimal precio)
            implements EventoDominio {
        public AlertaPrecioDisparada {
            Objects.requireNonNull(metadatos);
            Objects.requireNonNull(usuarioId);
            Objects.requireNonNull(alertaId);
        }
    }

    // ------------------------------------------------------------------ comisionistas

    /** Se asignó, cambió o terminó la relación inversionista–comisionista (SCRUM-52). */
    public record AsociacionComisionistaCambiada(MetadatosEvento metadatos, UUID inversionistaId,
                                                 UUID comisionistaId, String cambio) implements EventoDominio {
        public AsociacionComisionistaCambiada {
            Objects.requireNonNull(metadatos);
            Objects.requireNonNull(inversionistaId);
            Objects.requireNonNull(cambio);
        }
    }

    /** Un comisionista envió una propuesta de órdenes (SCRUM-53). */
    public record PropuestaOrdenesCreada(MetadatosEvento metadatos, UUID propuestaId, UUID inversionistaId,
                                         UUID comisionistaId, int numeroOrdenes) implements EventoDominio {
        public PropuestaOrdenesCreada {
            Objects.requireNonNull(metadatos);
            Objects.requireNonNull(propuestaId);
        }
    }
}
