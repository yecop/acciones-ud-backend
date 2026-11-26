package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.canal;

import co.edu.udistrital.accionesud.notificaciones.application.port.out.CanalEnvioPort;
import co.edu.udistrital.accionesud.notificaciones.domain.exception.EnvioFallidoException;
import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proveedor simulado de un canal externo (SCRUM-51): registra en el log lo que enviaría.
 * Se usa hasta integrar los proveedores reales (Twilio, SMTP, WhatsApp Business).
 *
 * <p>Para probar reintentos se le puede pedir que falle los próximos {@code n} envíos.</p>
 */
public class CanalSimuladoAdapter implements CanalEnvioPort {

    private static final Logger log = LoggerFactory.getLogger(CanalSimuladoAdapter.class);

    private final CanalNotificacion canal;
    private final List<UUID> enviadas = new CopyOnWriteArrayList<>();
    private final AtomicInteger fallosPendientes = new AtomicInteger();
    private final AtomicInteger intentos = new AtomicInteger();

    public CanalSimuladoAdapter(CanalNotificacion canal) {
        this.canal = Objects.requireNonNull(canal);
    }

    @Override
    public CanalNotificacion canal() {
        return canal;
    }

    @Override
    public void enviar(Notificacion notificacion) {
        intentos.incrementAndGet();
        if (fallosPendientes.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new EnvioFallidoException("Proveedor " + canal + " no disponible (simulado)");
        }
        enviadas.add(notificacion.id());
        log.info("[{} simulado] usuario={} tipo={} titulo=\"{}\"", canal, notificacion.usuarioId(),
                notificacion.tipo(), notificacion.titulo());
    }

    /** Hace fallar los próximos {@code n} envíos (simula una caída temporal del proveedor). */
    public void fallarProximos(int n) {
        fallosPendientes.set(n);
    }

    public List<UUID> enviadas() {
        return List.copyOf(enviadas);
    }

    public int intentos() {
        return intentos.get();
    }

    public void reiniciar() {
        enviadas.clear();
        fallosPendientes.set(0);
        intentos.set(0);
    }
}
