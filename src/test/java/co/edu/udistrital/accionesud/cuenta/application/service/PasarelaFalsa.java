package co.edu.udistrital.accionesud.cuenta.application.service;

import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoDisponibleException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Pasarela de pruebas: crea sesiones {@code cs_test_<movimiento>} y puede simular una caída. */
public class PasarelaFalsa implements PasarelaPagosPort {

    public final List<SolicitudPago> solicitudes = new ArrayList<>();
    private final Map<String, Boolean> abiertas = new ConcurrentHashMap<>();
    private boolean caida;

    public void caer(boolean caida) {
        this.caida = caida;
    }

    public void cerrar(String referencia) {
        abiertas.put(referencia, false);
    }

    public static String referenciaDe(java.util.UUID movimientoId) {
        return "cs_test_" + movimientoId;
    }

    @Override
    public boolean configurada() {
        return true;
    }

    @Override
    public synchronized SesionPago crearSesion(SolicitudPago solicitud) {
        if (caida) {
            throw new PasarelaNoDisponibleException("Stripe no respondió");
        }
        solicitudes.add(solicitud);
        String referencia = referenciaDe(solicitud.movimientoId());
        abiertas.put(referencia, true);
        return new SesionPago(referencia, "https://checkout.stripe.test/" + referencia,
                Instant.parse("2026-11-27T18:00:00Z"), true);
    }

    @Override
    public Optional<SesionPago> consultarSesion(String referencia) {
        Boolean abierta = abiertas.get(referencia);
        if (abierta == null) {
            return Optional.empty();
        }
        return Optional.of(new SesionPago(referencia, abierta ? "https://checkout.stripe.test/" + referencia : null,
                null, abierta));
    }
}
