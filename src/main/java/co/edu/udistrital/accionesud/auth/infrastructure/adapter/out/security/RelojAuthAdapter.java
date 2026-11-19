package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.security;

import co.edu.udistrital.accionesud.auth.application.port.out.RelojPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RelojAuthAdapter implements RelojPort {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
