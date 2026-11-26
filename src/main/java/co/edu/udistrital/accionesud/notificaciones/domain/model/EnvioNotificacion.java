package co.edu.udistrital.accionesud.notificaciones.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrega de una notificación por un canal externo (SCRUM-51). Un fallo temporal deja el envío
 * PENDIENTE con un próximo intento (espera exponencial); al agotar los intentos queda FALLIDO.
 * Reintentar nunca crea otra notificación: solo actualiza este registro.
 */
public record EnvioNotificacion(UUID notificacionId,
                                CanalNotificacion canal,
                                Estado estado,
                                int intentos,
                                String ultimoError,
                                Instant proximoIntento,
                                Instant enviadoEn) {

    public enum Estado { PENDIENTE, ENVIADO, FALLIDO }

    public static final int MAX_ERROR = 300;

    public EnvioNotificacion {
        Objects.requireNonNull(notificacionId, "La notificación es obligatoria");
        Objects.requireNonNull(canal, "El canal es obligatorio");
        Objects.requireNonNull(estado, "El estado es obligatorio");
        if (intentos < 0) {
            throw new IllegalArgumentException("Los intentos no pueden ser negativos");
        }
        if (ultimoError != null && ultimoError.length() > MAX_ERROR) {
            ultimoError = ultimoError.substring(0, MAX_ERROR);
        }
    }

    public static EnvioNotificacion pendiente(UUID notificacionId, CanalNotificacion canal, Instant ahora) {
        return new EnvioNotificacion(notificacionId, canal, Estado.PENDIENTE, 0, null, ahora, null);
    }

    public EnvioNotificacion exitoso(Instant ahora) {
        return new EnvioNotificacion(notificacionId, canal, Estado.ENVIADO, intentos + 1, null, null, ahora);
    }

    /**
     * Registra un fallo. Espera {@code base * 2^(intentos-1)} antes del siguiente intento; al llegar a
     * {@code maxIntentos} el envío queda FALLIDO definitivamente.
     */
    public EnvioNotificacion fallido(String error, Instant ahora, int maxIntentos, Duration base) {
        int nuevosIntentos = intentos + 1;
        if (nuevosIntentos >= maxIntentos) {
            return new EnvioNotificacion(notificacionId, canal, Estado.FALLIDO, nuevosIntentos, error, null, null);
        }
        Duration espera = base.multipliedBy(1L << Math.min(nuevosIntentos - 1, 16));
        return new EnvioNotificacion(notificacionId, canal, Estado.PENDIENTE, nuevosIntentos, error,
                ahora.plus(espera), null);
    }
}
