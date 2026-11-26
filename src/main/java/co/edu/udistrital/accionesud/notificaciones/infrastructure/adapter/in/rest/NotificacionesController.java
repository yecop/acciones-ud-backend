package co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.notificaciones.application.dto.FiltroNotificaciones;
import co.edu.udistrital.accionesud.notificaciones.application.port.in.ConsultarNotificacionesUseCase;
import co.edu.udistrital.accionesud.notificaciones.domain.model.TipoNotificacion;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.rest.dto.MarcadasResponse;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.rest.dto.NotificacionResponse;
import co.edu.udistrital.accionesud.notificaciones.infrastructure.adapter.in.rest.dto.PaginaNotificacionesResponse;
import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bandeja de notificaciones del usuario autenticado (SCRUM-51). El usuario sale del token
 * (subject = id), así que nadie puede leer ni marcar notificaciones ajenas: una ajena responde
 * 404 igual que una inexistente.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
@PreAuthorize("hasAnyRole('INVERSIONISTA', 'COMISIONISTA')")
public class NotificacionesController {

    static final int TAMANO_POR_DEFECTO = 20;

    private final ConsultarNotificacionesUseCase notificaciones;

    public NotificacionesController(ConsultarNotificacionesUseCase notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping
    public PaginaNotificacionesResponse listar(Authentication autenticacion,
                                               @RequestParam(required = false) TipoNotificacion tipo,
                                               @RequestParam(required = false) String desde,
                                               @RequestParam(required = false) Boolean leida,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "" + TAMANO_POR_DEFECTO) int tamano) {
        List<ValidacionException.ErrorCampo> errores = new ArrayList<>();
        if (pagina < 0) {
            errores.add(new ValidacionException.ErrorCampo("pagina", "debe ser mayor o igual a 0"));
        }
        if (tamano < 1 || tamano > FiltroNotificaciones.TAMANO_MAXIMO) {
            errores.add(new ValidacionException.ErrorCampo("tamano",
                    "debe estar entre 1 y " + FiltroNotificaciones.TAMANO_MAXIMO));
        }
        Instant desdeInstante = null;
        if (desde != null && !desde.isBlank()) {
            try {
                desdeInstante = interpretarFecha(desde.strip());
            } catch (DateTimeException e) {
                errores.add(new ValidacionException.ErrorCampo("desde",
                        "debe ser una fecha ISO 8601 (2026-11-01 o 2026-11-01T13:00:00Z)"));
            }
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        FiltroNotificaciones filtro = new FiltroNotificaciones(usuarioId(autenticacion), tipo, desdeInstante, leida,
                pagina, tamano);
        return PaginaNotificacionesResponse.desde(notificaciones.listar(filtro));
    }

    @PatchMapping("/{id}/leida")
    public NotificacionResponse marcarLeida(Authentication autenticacion, @PathVariable UUID id) {
        return NotificacionResponse.desde(notificaciones.marcarLeida(usuarioId(autenticacion), id));
    }

    @PatchMapping("/leidas")
    public MarcadasResponse marcarTodasLeidas(Authentication autenticacion) {
        return new MarcadasResponse(notificaciones.marcarTodasLeidas(usuarioId(autenticacion)));
    }

    /** Acepta un instante con zona ({@code 2026-11-01T13:00:00Z}) o una fecha (inicio del día en UTC). */
    static Instant interpretarFecha(String valor) {
        if (valor.length() == 10) {
            return LocalDate.parse(valor).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        return OffsetDateTime.parse(valor).toInstant();
    }

    private static UUID usuarioId(Authentication autenticacion) {
        try {
            return UUID.fromString(autenticacion.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token no identifica a un usuario");
        }
    }
}
