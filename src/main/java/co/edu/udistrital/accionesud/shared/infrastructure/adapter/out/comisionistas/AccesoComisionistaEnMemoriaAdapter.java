package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.comisionistas;

import co.edu.udistrital.accionesud.shared.application.port.out.AccesoComisionistaPort;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relaciones inversionista–comisionista en memoria (SCRUM-33). Es el adaptador por defecto
 * mientras SCRUM-52 no las persista ({@code accionesud.integraciones.comisionistas=memoria}).
 * Por defecto nadie tiene relación, así que el acceso se deniega: es la opción segura.
 * Cada inversionista tiene como máximo un comisionista vigente.
 */
public class AccesoComisionistaEnMemoriaAdapter implements AccesoComisionistaPort {

    /** inversionistaId → comisionistaId vigente. */
    private final Map<UUID, UUID> vigentes = new ConcurrentHashMap<>();

    @Override
    public boolean puedeAcceder(UUID comisionistaId, UUID inversionistaId) {
        if (comisionistaId == null || inversionistaId == null) {
            return false;
        }
        return comisionistaId.equals(vigentes.get(inversionistaId));
    }

    @Override
    public Optional<UUID> comisionistaVigenteDe(UUID inversionistaId) {
        return Optional.ofNullable(inversionistaId == null ? null : vigentes.get(inversionistaId));
    }

    /** Asigna (o reemplaza) el comisionista vigente del inversionista. */
    public void asociar(UUID inversionistaId, UUID comisionistaId) {
        vigentes.put(Objects.requireNonNull(inversionistaId), Objects.requireNonNull(comisionistaId));
    }

    public void finalizar(UUID inversionistaId) {
        vigentes.remove(inversionistaId);
    }
}
