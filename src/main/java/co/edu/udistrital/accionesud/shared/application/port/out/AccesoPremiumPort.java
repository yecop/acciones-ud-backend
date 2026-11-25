package co.edu.udistrital.accionesud.shared.application.port.out;

import co.edu.udistrital.accionesud.shared.domain.exception.FuncionPremiumRequeridaException;
import co.edu.udistrital.accionesud.shared.domain.premium.PlanSuscripcion;

import java.util.UUID;

/**
 * Consulta si un usuario tiene acceso premium (SCRUM-33). Lo implementa SCRUM-54 con Stripe;
 * las funciones premium (watchlist, alertas, reportes) solo dependen de este puerto.
 */
public interface AccesoPremiumPort {

    PlanSuscripcion planDe(UUID usuarioId);

    default boolean esPremium(UUID usuarioId) {
        return planDe(usuarioId) == PlanSuscripcion.PREMIUM;
    }

    /** @throws FuncionPremiumRequeridaException (HTTP 403 {@code FUNCION_PREMIUM}) si no es premium. */
    default void exigirPremium(UUID usuarioId) {
        if (!esPremium(usuarioId)) {
            throw new FuncionPremiumRequeridaException();
        }
    }
}
