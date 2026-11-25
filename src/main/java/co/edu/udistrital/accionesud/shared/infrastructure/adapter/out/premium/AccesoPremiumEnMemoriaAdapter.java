package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.premium;

import co.edu.udistrital.accionesud.shared.application.port.out.AccesoPremiumPort;
import co.edu.udistrital.accionesud.shared.domain.premium.PlanSuscripcion;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acceso premium en memoria (SCRUM-33). Es el adaptador por defecto mientras SCRUM-54 no conecte
 * Stripe ({@code accionesud.integraciones.premium=simulado}): todos son BASICO salvo los ids
 * configurados en {@code accionesud.integraciones.premium-usuarios} o marcados con
 * {@link #otorgar}. Sirve de fake para las funciones premium (watchlist, alertas).
 */
public class AccesoPremiumEnMemoriaAdapter implements AccesoPremiumPort {

    private final Set<UUID> premium = ConcurrentHashMap.newKeySet();

    public AccesoPremiumEnMemoriaAdapter() {
    }

    public AccesoPremiumEnMemoriaAdapter(Collection<UUID> usuariosPremium) {
        usuariosPremium.forEach(this::otorgar);
    }

    @Override
    public PlanSuscripcion planDe(UUID usuarioId) {
        return usuarioId != null && premium.contains(usuarioId) ? PlanSuscripcion.PREMIUM : PlanSuscripcion.BASICO;
    }

    public void otorgar(UUID usuarioId) {
        premium.add(Objects.requireNonNull(usuarioId));
    }

    public void revocar(UUID usuarioId) {
        premium.remove(usuarioId);
    }
}
