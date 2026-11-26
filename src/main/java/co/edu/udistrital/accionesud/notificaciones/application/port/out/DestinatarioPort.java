package co.edu.udistrital.accionesud.notificaciones.application.port.out;

import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Contacto;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Datos del destinatario que viven en otros módulos (cuentas y preferencias de SCRUM-22). */
public interface DestinatarioPort {

    /** Usuario titular de la cuenta de corretaje. */
    Optional<UUID> titularDeCuenta(UUID cuentaId);

    /**
     * Canales externos por los que el usuario quiere recibir el evento del catálogo
     * {@code evento_notificacion}: los canales elegidos, solo si también eligió ese evento.
     */
    Set<CanalNotificacion> canalesPara(UUID usuarioId, String eventoPreferencia);

    /** Correo y celular del usuario; vacío si el usuario no existe. */
    Optional<Contacto> contacto(UUID usuarioId);
}
