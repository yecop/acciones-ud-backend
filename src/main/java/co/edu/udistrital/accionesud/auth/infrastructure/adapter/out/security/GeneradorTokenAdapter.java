package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.security;

import co.edu.udistrital.accionesud.auth.application.port.out.GeneradorTokenPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class GeneradorTokenAdapter implements GeneradorTokenPort {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generar() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
