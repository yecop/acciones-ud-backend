package co.edu.udistrital.accionesud.notificaciones.application.port.out;

import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;

/** Proveedor de un canal externo (SMS, correo, WhatsApp). */
public interface CanalEnvioPort {

    CanalNotificacion canal();

    /** @throws co.edu.udistrital.accionesud.notificaciones.domain.exception.EnvioFallidoException si el proveedor no pudo entregar. */
    void enviar(Notificacion notificacion);
}
