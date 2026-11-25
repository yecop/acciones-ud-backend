package co.edu.udistrital.accionesud.shared.domain.auditoria;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento auditable: quién hizo qué, sobre qué entidad, cuándo y con qué traceId (SCRUM-33).
 *
 * <p>Es inmutable y en base de datos tampoco se puede editar ni borrar (SCRUM-42).</p>
 *
 * @param ocurridoEn instante de la acción.
 * @param actorId    usuario que la realizó; {@code null} para procesos del sistema.
 * @param accion     código estable de la acción (p. ej. {@code ORDEN_CREADA}).
 * @param entidad    tipo de entidad afectada (p. ej. {@code ORDEN}).
 * @param entidadId  identificador de la entidad afectada.
 * @param resultado  resultado de la acción.
 * @param detalle    datos adicionales sin secretos (montos, estados, motivos).
 * @param traceId    traceId de la solicitud; obligatorio.
 */
public record RegistroAuditoria(Instant ocurridoEn,
                                UUID actorId,
                                String accion,
                                String entidad,
                                String entidadId,
                                ResultadoAuditoria resultado,
                                Map<String, String> detalle,
                                String traceId) {

    public static final int MAX_ACCION = 60;
    public static final int MAX_ENTIDAD = 40;
    public static final int MAX_ENTIDAD_ID = 80;

    public RegistroAuditoria {
        Objects.requireNonNull(ocurridoEn, "La fecha es obligatoria");
        Objects.requireNonNull(resultado, "El resultado es obligatorio");
        accion = texto(accion, "accion", MAX_ACCION);
        entidad = texto(entidad, "entidad", MAX_ENTIDAD);
        if (entidadId != null && entidadId.length() > MAX_ENTIDAD_ID) {
            throw new IllegalArgumentException("El id de la entidad supera " + MAX_ENTIDAD_ID + " caracteres");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("El traceId es obligatorio en la auditoría");
        }
        detalle = detalle == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(detalle));
    }

    private static String texto(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio en la auditoría");
        }
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("El campo " + campo + " supera " + maximo + " caracteres");
        }
        return limpio;
    }
}
