package co.edu.udistrital.accionesud.notificaciones.domain.exception;

import co.edu.udistrital.accionesud.shared.domain.exception.DomainException;

/**
 * Un canal externo no pudo entregar la notificación.
 *
 * <ul>
 *   <li>{@link #temporal}: el proveedor no respondió o rechazó por carga; se reintenta.</li>
 *   <li>{@link #permanente}: reintentar no sirve (sin correo, dirección inválida); el envío queda
 *   FALLIDO sin gastar más intentos.</li>
 * </ul>
 */
public class EnvioFallidoException extends DomainException {

    private final boolean reintentable;

    private EnvioFallidoException(String mensaje, boolean reintentable, Throwable causa) {
        super(reintentable ? "ENVIO_FALLIDO" : "ENVIO_RECHAZADO", mensaje, causa);
        this.reintentable = reintentable;
    }

    public static EnvioFallidoException temporal(String mensaje) {
        return new EnvioFallidoException(mensaje, true, null);
    }

    public static EnvioFallidoException temporal(String mensaje, Throwable causa) {
        return new EnvioFallidoException(mensaje, true, causa);
    }

    public static EnvioFallidoException permanente(String mensaje) {
        return new EnvioFallidoException(mensaje, false, null);
    }

    public static EnvioFallidoException permanente(String mensaje, Throwable causa) {
        return new EnvioFallidoException(mensaje, false, causa);
    }

    public boolean reintentable() {
        return reintentable;
    }
}
