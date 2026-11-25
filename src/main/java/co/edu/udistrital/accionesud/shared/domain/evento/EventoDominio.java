package co.edu.udistrital.accionesud.shared.domain.evento;

/**
 * Hecho relevante del negocio que otros módulos pueden consumir (SCRUM-33).
 *
 * <p>Los eventos se publican con {@code PublicadorEventosPort} y solo se entregan a los
 * consumidores después de que la transacción que los originó confirma: si hay rollback,
 * no salen. Los consumidores deben ser idempotentes usando {@link MetadatosEvento#id()}.</p>
 *
 * <p>Cada evento es un {@code record} inmutable con sus metadatos y los datos mínimos que
 * necesita un consumidor (notificaciones, auditoría, reportes), sin entidades completas.</p>
 */
public interface EventoDominio {

    MetadatosEvento metadatos();

    /** Nombre estable del evento (por defecto, el nombre de la clase). */
    default String tipo() {
        return getClass().getSimpleName();
    }
}
