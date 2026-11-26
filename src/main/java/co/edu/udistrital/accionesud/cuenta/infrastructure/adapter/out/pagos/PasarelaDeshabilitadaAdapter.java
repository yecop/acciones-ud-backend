package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.pagos;

import co.edu.udistrital.accionesud.cuenta.application.port.out.PasarelaPagosPort;
import co.edu.udistrital.accionesud.cuenta.domain.exception.PasarelaNoConfiguradaException;
import java.util.Optional;

/**
 * Pasarela por defecto cuando no hay Stripe configurado (perfil test, CI, dev sin claves): la
 * recarga responde 503 {@code PASARELA_NO_CONFIGURADA}. Los depósitos simulados siguen funcionando.
 */
public class PasarelaDeshabilitadaAdapter implements PasarelaPagosPort {

    @Override
    public boolean configurada() {
        return false;
    }

    @Override
    public SesionPago crearSesion(SolicitudPago solicitud) {
        throw new PasarelaNoConfiguradaException();
    }

    @Override
    public Optional<SesionPago> consultarSesion(String referencia) {
        return Optional.empty();
    }
}
