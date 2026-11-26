package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.out.persistencia;

import co.edu.udistrital.accionesud.notificaciones.application.dto.FiltroNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.dto.PaginaNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.DestinatarioPort;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.EnvioRepositoryPort;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.NotificacionRepositoryPort;
import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Contacto;
import co.edu.udistrital.accionesud.notificaciones.domain.model.EnvioNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacenamiento en memoria para entornos sin base de datos (perfil {@code test}) con el mismo
 * contrato que los adaptadores JDBC: notificaciones únicas por id, envíos únicos por
 * (notificación, canal) y reserva de pendientes. Permite configurar titulares y preferencias.
 */
public class NotificacionesEnMemoriaAdapter
        implements NotificacionRepositoryPort, EnvioRepositoryPort, DestinatarioPort {

    private static final Comparator<Notificacion> RECIENTES_PRIMERO = Comparator
            .comparing(Notificacion::creadaEn).thenComparing(Notificacion::id).reversed();

    private final Map<UUID, Notificacion> notificaciones = new LinkedHashMap<>();
    private final Map<String, EnvioNotificacion> envios = new LinkedHashMap<>();
    private final Map<UUID, UUID> titulares = new ConcurrentHashMap<>();
    private final Map<UUID, Set<CanalNotificacion>> canales = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> eventos = new ConcurrentHashMap<>();
    private final Map<UUID, Contacto> contactos = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ configuración (pruebas)

    public void registrarCuenta(UUID cuentaId, UUID usuarioId) {
        titulares.put(cuentaId, usuarioId);
    }

    public void registrarPreferencias(UUID usuarioId, Set<CanalNotificacion> canalesElegidos, Set<String> eventosElegidos) {
        canales.put(usuarioId, canalesElegidos.isEmpty() ? Set.of() : EnumSet.copyOf(canalesElegidos));
        eventos.put(usuarioId, Set.copyOf(eventosElegidos));
    }

    public void registrarContacto(UUID usuarioId, Contacto contacto) {
        contactos.put(usuarioId, contacto);
    }

    public synchronized void limpiar() {
        notificaciones.clear();
        envios.clear();
        titulares.clear();
        canales.clear();
        eventos.clear();
        contactos.clear();
    }

    // ------------------------------------------------------------------ destinatarios

    @Override
    public Optional<UUID> titularDeCuenta(UUID cuentaId) {
        return Optional.ofNullable(titulares.get(cuentaId));
    }

    @Override
    public Set<CanalNotificacion> canalesPara(UUID usuarioId, String eventoPreferencia) {
        if (!eventos.getOrDefault(usuarioId, Set.of()).contains(eventoPreferencia)) {
            return Set.of();
        }
        return canales.getOrDefault(usuarioId, Set.of());
    }

    @Override
    public Optional<Contacto> contacto(UUID usuarioId) {
        return Optional.ofNullable(contactos.get(usuarioId));
    }

    // ------------------------------------------------------------------ notificaciones

    @Override
    public synchronized boolean guardarSiNoExiste(Notificacion notificacion) {
        return notificaciones.putIfAbsent(notificacion.id(), notificacion) == null;
    }

    @Override
    public synchronized Optional<Notificacion> buscarDelUsuario(UUID notificacionId, UUID usuarioId) {
        return buscar(notificacionId).filter(n -> n.usuarioId().equals(usuarioId));
    }

    @Override
    public synchronized Optional<Notificacion> buscar(UUID notificacionId) {
        return Optional.ofNullable(notificaciones.get(notificacionId));
    }

    @Override
    public synchronized void marcarLeida(UUID notificacionId, UUID usuarioId, Instant leidaEn) {
        buscarDelUsuario(notificacionId, usuarioId)
                .ifPresent(n -> notificaciones.put(n.id(), n.marcarLeida(leidaEn)));
    }

    @Override
    public synchronized int marcarTodasLeidas(UUID usuarioId, Instant leidaEn) {
        int marcadas = 0;
        for (Notificacion n : new ArrayList<>(notificaciones.values())) {
            if (n.usuarioId().equals(usuarioId) && !n.leida()) {
                notificaciones.put(n.id(), n.marcarLeida(leidaEn));
                marcadas++;
            }
        }
        return marcadas;
    }

    @Override
    public synchronized PaginaNotificaciones listar(FiltroNotificaciones filtro) {
        List<Notificacion> coincidentes = notificaciones.values().stream()
                .filter(n -> n.usuarioId().equals(filtro.usuarioId()))
                .filter(n -> filtro.tipo() == null || n.tipo() == filtro.tipo())
                .filter(n -> filtro.desde() == null || !n.creadaEn().isBefore(filtro.desde()))
                .filter(n -> filtro.leida() == null || n.leida() == filtro.leida())
                .sorted(RECIENTES_PRIMERO)
                .toList();
        List<Notificacion> pagina = coincidentes.stream()
                .skip(filtro.desplazamiento())
                .limit(filtro.tamano())
                .toList();
        return new PaginaNotificaciones(pagina, filtro.pagina(), filtro.tamano(), coincidentes.size());
    }

    // ------------------------------------------------------------------ envíos

    @Override
    public synchronized void guardarSiNoExiste(EnvioNotificacion envio) {
        envios.putIfAbsent(clave(envio.notificacionId(), envio.canal()), envio);
    }

    @Override
    public synchronized void actualizar(EnvioNotificacion envio) {
        envios.replace(clave(envio.notificacionId(), envio.canal()), envio);
    }

    @Override
    public synchronized Optional<EnvioNotificacion> buscar(UUID notificacionId, CanalNotificacion canal) {
        return Optional.ofNullable(envios.get(clave(notificacionId, canal)));
    }

    @Override
    public synchronized List<EnvioNotificacion> reclamarPendientes(Instant ahora, Instant reservadoHasta, int limite) {
        List<EnvioNotificacion> reclamados = envios.values().stream()
                .filter(e -> e.estado() == EnvioNotificacion.Estado.PENDIENTE)
                .filter(e -> !e.proximoIntento().isAfter(ahora))
                .sorted(Comparator.comparing(EnvioNotificacion::proximoIntento))
                .limit(limite)
                .map(e -> new EnvioNotificacion(e.notificacionId(), e.canal(), e.estado(), e.intentos(),
                        e.ultimoError(), reservadoHasta, e.enviadoEn()))
                .toList();
        reclamados.forEach(e -> envios.put(clave(e.notificacionId(), e.canal()), e));
        return reclamados;
    }

    private static String clave(UUID notificacionId, CanalNotificacion canal) {
        return notificacionId + "|" + canal;
    }
}
