package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.security;

import co.edu.udistrital.accionesud.auth.application.port.out.VerificadorContrasenaPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class VerificadorContrasenaAdapter implements VerificadorContrasenaPort {

    private final PasswordEncoder passwordEncoder;

    public VerificadorContrasenaAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean coincide(String contrasena, String contrasenaHash) {
        return passwordEncoder.matches(contrasena, contrasenaHash);
    }
}
