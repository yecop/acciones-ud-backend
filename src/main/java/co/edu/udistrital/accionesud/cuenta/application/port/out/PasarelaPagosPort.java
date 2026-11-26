package co.edu.udistrital.accionesud.cuenta.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pasarela de pagos para recargar saldo (SCRUM-46). La implementa Stripe Checkout; sin
 * configuración se usa un adaptador deshabilitado.
 *
 * <p>La pasarela solo crea la página de pago. El saldo nunca se acredita aquí: se acredita cuando
 * llega el aviso firmado del proveedor (webhook).</p>
 */
public interface PasarelaPagosPort {

    /** {@code true} si la pasarela tiene sus claves y puede crear pagos. */
    boolean configurada();

    /**
     * Crea la sesión de pago. Repetirla con el mismo {@code movimientoId} no crea otra (la
     * pasarela recibe ese id como clave de idempotencia).
     *
     * @throws co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoDisponibleException
     *         si el proveedor no responde o rechaza la solicitud.
     */
    SesionPago crearSesion(SolicitudPago solicitud);

    /** Estado actual de una sesión creada antes, si el proveedor la conoce. */
    Optional<SesionPago> consultarSesion(String referencia);

    record SolicitudPago(UUID movimientoId, UUID cuentaId, BigDecimal monto, String moneda) {
        public SolicitudPago {
            Objects.requireNonNull(movimientoId);
            Objects.requireNonNull(cuentaId);
            Objects.requireNonNull(monto);
            Objects.requireNonNull(moneda);
        }
    }

    /**
     * @param referencia id de la sesión en el proveedor (p. ej. {@code cs_test_...}).
     * @param url        página de pago a la que se redirige al usuario; {@code null} si ya no está abierta.
     * @param abierta    {@code true} mientras el usuario todavía puede pagar.
     */
    record SesionPago(String referencia, String url, Instant expiraEn, boolean abierta) {
        public SesionPago {
            Objects.requireNonNull(referencia);
        }
    }
}
