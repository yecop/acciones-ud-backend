package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.auth.application.port.in.RegistrarUsuarioUseCase;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto.RegistroRequest;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto.RegistroResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Perfil test sin base de datos (application-test.yml excluye DataSource/Flyway).
@Profile("!test")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrarUsuarioUseCase registrarUsuario;

    public AuthController(RegistrarUsuarioUseCase registrarUsuario) {
        this.registrarUsuario = registrarUsuario;
    }

    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RegistroResponse.desde(registrarUsuario.registrar(request.aComando())));
    }
}