package co.edu.udistrital.accionesud.notificaciones.application.port.out;

import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.EnvioNotificacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Estado de entrega por canal externo; una fila por (notificación, canal). */
public interface EnvioRepositoryPort {

    /** No hace nada si ya existe el envío de esa notificación por ese canal. */
    void guardarSiNoExiste(EnvioNotificacion envio);

    void actualizar(EnvioNotificacion envio);

    Optional<EnvioNotificacion> buscar(UUID notificacionId, CanalNotificacion canal);

    /**
     * Toma hasta {@code limite} envíos PENDIENTES vencidos y los reserva hasta {@code reservadoHasta}
     * para que otra instancia no los envíe a la vez. Si el proceso muere, se retoman al vencer la reserva.
     */
    List<EnvioNotificacion> reclamarPendientes(Instant ahora, Instant reservadoHasta, int limite);
}
