package co.edu.udistrital.accionesud.notificaciones.application.service;

import co.edu.udistrital.accionesud.notificaciones.application.dto.FiltroNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.dto.PaginaNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.port.in.ConsultarNotificacionesUseCase;
import co.edu.udistrital.accionesud.notificaciones.application.port.in.ProcesarEventoUseCase;
import co.edu.udistrital.accionesud.notificaciones.application.port.in.ReintentarEnviosUseCase;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.CanalEnvioPort;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.DestinatarioPort;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.EnvioRepositoryPort;
import co.edu.udistrital.accionesud.notificaciones.application.port.out.NotificacionRepositoryPort;
import co.edu.udistrital.accionesud.notificaciones.domain.exception.EnvioFallidoException;
import co.edu.udistrital.accionesud.notificaciones.domain.exception.NotificacionNoEncontradaException;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Aviso;
import co.edu.udistrital.accionesud.notificaciones.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Contacto;
import co.edu.udistrital.accionesud.notificaciones.domain.model.EnvioNotificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.Notificacion;
import co.edu.udistrital.accionesud.notificaciones.domain.model.TraductorEventos;
import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de notificaciones (SCRUM-51).
 *
 * <ol>
 *   <li>Un evento de dominio se traduce en avisos y cada aviso en una notificación persistida para
 *   su destinatario. El id es determinístico, así que reprocesar el evento no la duplica.</li>
 *   <li>Si el tipo está en el catálogo de preferencias, se crea un envío PENDIENTE por cada canal
 *   que el usuario eligió y se intenta de inmediato, fuera de la transacción.</li>
 *   <li>Un fallo deja el envío PENDIENTE con espera exponencial; {@link #reintentarPendientes()} lo
 *   retoma. Reintentar solo actualiza el envío: la notificación no se vuelve a crear.</li>
 * </ol>
 */
public class NotificacionesService
        implements ConsultarNotificacionesUseCase, ProcesarEventoUseCase, ReintentarEnviosUseCase {

    private static final System.Logger log = System.getLogger(NotificacionesService.class.getName());

    /** Parámetros de reintento de los canales externos. */
    public record PoliticaReintento(int maxIntentos, Duration esperaBase, Duration reserva, int lote) {
        public PoliticaReintento {
            if (maxIntentos < 1 || lote < 1) {
                throw new IllegalArgumentException("maxIntentos y lote deben ser positivos");
            }
            Objects.requireNonNull(esperaBase);
            Objects.requireNonNull(reserva);
        }
    }

    private final NotificacionRepositoryPort notificaciones;
    private final EnvioRepositoryPort envios;
    private final DestinatarioPort destinatarios;
    private final Map<CanalNotificacion, CanalEnvioPort> canales;
    private final TransaccionPort transaccion;
    private final TraductorEventos traductor = new TraductorEventos();
    private final PoliticaReintento politica;
    private final Clock reloj;

    public NotificacionesService(NotificacionRepositoryPort notificaciones, EnvioRepositoryPort envios,
                                 DestinatarioPort destinatarios, List<CanalEnvioPort> canales,
                                 TransaccionPort transaccion, PoliticaReintento politica, Clock reloj) {
        this.notificaciones = Objects.requireNonNull(notificaciones);
        this.envios = Objects.requireNonNull(envios);
        this.destinatarios = Objects.requireNonNull(destinatarios);
        this.transaccion = Objects.requireNonNull(transaccion);
        this.politica = Objects.requireNonNull(politica);
        this.reloj = Objects.requireNonNull(reloj);
        this.canales = new EnumMap<>(CanalNotificacion.class);
        canales.forEach(c -> this.canales.put(c.canal(), c));
    }

    // ------------------------------------------------------------------ eventos

    @Override
    public int procesar(EventoDominio evento) {
        Objects.requireNonNull(evento, "El evento es obligatorio");
        List<Aviso> avisos = traductor.traducir(evento);
        if (avisos.isEmpty()) {
            return 0;
        }
        Instant ahora = reloj.instant();
        List<Creada> creadas = transaccion.enTransaccion(() -> crear(evento, avisos, ahora));
        creadas.forEach(c -> c.envios().forEach(envio -> intentar(envio, c.notificacion())));
        return creadas.size();
    }

    private record Creada(Notificacion notificacion, List<EnvioNotificacion> envios) {
    }

    private List<Creada> crear(EventoDominio evento, List<Aviso> avisos, Instant ahora) {
        List<Creada> creadas = new ArrayList<>();
        for (Aviso aviso : avisos) {
            Optional<UUID> usuario = resolver(aviso.destinatario());
            if (usuario.isEmpty()) {
                log.log(System.Logger.Level.WARNING, "Sin destinatario para {0} del evento {1} ({2})",
                        aviso.tipo(), evento.tipo(), evento.metadatos().id());
                continue;
            }
            Notificacion notificacion = Notificacion.nueva(usuario.get(), evento.metadatos().id(), aviso.tipo(),
                    aviso.titulo(), aviso.mensaje(), aviso.referenciaId(), evento.metadatos().ocurridoEn(),
                    evento.metadatos().traceId());
            if (!notificaciones.guardarSiNoExiste(notificacion)) {
                continue; // Evento reprocesado: ya existe, no se duplica ni se reenvía.
            }
            Set<CanalNotificacion> elegidos = aviso.tipo().eventoPreferencia()
                    .map(e -> destinatarios.canalesPara(usuario.get(), e))
                    .orElse(Set.of());
            List<EnvioNotificacion> nuevos = new ArrayList<>();
            for (CanalNotificacion canal : elegidos) {
                // Reservado mientras se intenta de inmediato; si el proceso cae, el reintento lo retoma.
                EnvioNotificacion envio = EnvioNotificacion.pendiente(notificacion.id(), canal,
                        ahora.plus(politica.reserva()));
                envios.guardarSiNoExiste(envio);
                nuevos.add(envio);
            }
            creadas.add(new Creada(notificacion, nuevos));
        }
        return creadas;
    }

    private Optional<UUID> resolver(Aviso.Destinatario destinatario) {
        if (destinatario instanceof Aviso.Usuario u) {
            return Optional.of(u.usuarioId());
        }
        return destinatarios.titularDeCuenta(((Aviso.TitularDeCuenta) destinatario).cuentaId());
    }

    // ------------------------------------------------------------------ envíos

    @Override
    public int reintentarPendientes() {
        Instant ahora = reloj.instant();
        List<EnvioNotificacion> pendientes = transaccion.enTransaccion(
                () -> envios.reclamarPendientes(ahora, ahora.plus(politica.reserva()), politica.lote()));
        for (EnvioNotificacion envio : pendientes) {
            Optional<Notificacion> notificacion = notificaciones.buscar(envio.notificacionId());
            if (notificacion.isEmpty()) {
                EnvioNotificacion huerfano = envio.fallido("La notificación ya no existe", ahora, 1, politica.esperaBase());
                transaccion.enTransaccion(() -> envios.actualizar(huerfano));
                continue;
            }
            intentar(envio, notificacion.get());
        }
        return pendientes.size();
    }

    private void intentar(EnvioNotificacion envio, Notificacion notificacion) {
        CanalEnvioPort canal = canales.get(envio.canal());
        EnvioNotificacion resultado;
        try {
            if (canal == null) {
                throw new IllegalStateException("No hay proveedor configurado para " + envio.canal());
            }
            Contacto contacto = destinatarios.contacto(notificacion.usuarioId())
                    .orElseThrow(() -> EnvioFallidoException.permanente("El usuario ya no existe"));
            canal.enviar(notificacion, contacto);
            resultado = envio.exitoso(reloj.instant());
        } catch (RuntimeException e) {
            // Un rechazo permanente agota el envío en este intento: reintentar no lo arreglaría.
            boolean reintentable = !(e instanceof EnvioFallidoException f) || f.reintentable();
            int maxIntentos = reintentable ? politica.maxIntentos() : envio.intentos() + 1;
            resultado = envio.fallido(describir(e), reloj.instant(), maxIntentos, politica.esperaBase());
            log.log(System.Logger.Level.WARNING, "Falló el envío {0} de la notificación {1} (intento {2}, {3}): {4}",
                    envio.canal(), notificacion.id(), resultado.intentos(), resultado.estado(), e.getMessage());
        }
        EnvioNotificacion guardar = resultado;
        try {
            // Transacción propia: si se llama desde el afterCommit del publicador, no se mezcla con la ya confirmada.
            transaccion.enTransaccion(() -> envios.actualizar(guardar));
        } catch (RuntimeException e) {
            // Si no se puede guardar el resultado, la reserva vence y el envío se retoma.
            log.log(System.Logger.Level.ERROR, "No se pudo guardar el estado del envío " + envio.canal()
                    + " de la notificación " + notificacion.id(), e);
        }
    }

    private static String describir(RuntimeException e) {
        String mensaje = e.getMessage();
        return mensaje == null || mensaje.isBlank() ? e.getClass().getSimpleName() : mensaje;
    }

    // ------------------------------------------------------------------ bandeja

    @Override
    public PaginaNotificaciones listar(FiltroNotificaciones filtro) {
        return notificaciones.listar(filtro);
    }

    @Override
    public Notificacion marcarLeida(UUID usuarioId, UUID notificacionId) {
        Notificacion actual = notificaciones.buscarDelUsuario(notificacionId, usuarioId)
                .orElseThrow(NotificacionNoEncontradaException::new);
        if (actual.leida()) {
            return actual;
        }
        notificaciones.marcarLeida(notificacionId, usuarioId, reloj.instant());
        return notificaciones.buscarDelUsuario(notificacionId, usuarioId)
                .orElseThrow(NotificacionNoEncontradaException::new);
    }

    @Override
    public int marcarTodasLeidas(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "El usuario es obligatorio");
        return notificaciones.marcarTodasLeidas(usuarioId, reloj.instant());
    }
}
