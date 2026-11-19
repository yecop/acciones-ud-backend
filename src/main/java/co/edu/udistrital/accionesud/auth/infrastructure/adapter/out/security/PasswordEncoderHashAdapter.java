package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.security;

import co.edu.udistrital.accionesud.auth.application.port.out.HashContrasenaPort;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordEncoderHashAdapter implements HashContrasenaPort {

    private final PasswordEncoder encoder;

    public PasswordEncoderHashAdapter(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hashear(String contrasenaPlana) {
        return encoder.encode(contrasenaPlana);
    }
}