package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.security;

import co.edu.udistrital.accionesud.auth.application.port.out.GeneradorCodigoMfaPort;
import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class GeneradorCodigoMfaAdapter implements GeneradorCodigoMfaPort {

    private final SecureRandom random = new SecureRandom();
    private final AuthProperties properties;

    public GeneradorCodigoMfaAdapter(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generar() {
        int longitud = properties.getMfa().getLongitudCodigo();
        int limite = (int) Math.pow(10, longitud);
        return String.format("%0" + longitud + "d", random.nextInt(limite));
    }
}
