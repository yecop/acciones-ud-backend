package co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.perfil.application.port.in.ActualizarPerfilUseCase;
import co.edu.udistrital.accionesud.perfil.application.port.in.ConsultarPerfilUseCase;
import co.edu.udistrital.accionesud.perfil.application.port.in.GestionarPreferenciasUseCase;
import co.edu.udistrital.accionesud.perfil.application.port.in.IdentificarUsuarioUseCase;
import co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest.dto.ActualizarPerfilRequest;
import co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest.dto.PerfilResponse;
import co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest.dto.PreferenciasRequest;
import co.edu.udistrital.accionesud.perfil.infrastructure.adapter.in.rest.dto.PreferenciasResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Perfil test sin base de datos (application-test.yml excluye DataSource/Flyway).
@Profile("!test")
@RestController
@RequestMapping("/api/v1/perfil")
@PreAuthorize("hasRole('INVERSIONISTA')")
public class PerfilController {

    private final IdentificarUsuarioUseCase identificar;
    private final ConsultarPerfilUseCase consultarPerfil;
    private final ActualizarPerfilUseCase actualizarPerfil;
    private final GestionarPreferenciasUseCase preferencias;

    public PerfilController(IdentificarUsuarioUseCase identificar,
                            ConsultarPerfilUseCase consultarPerfil,
                            ActualizarPerfilUseCase actualizarPerfil,
                            GestionarPreferenciasUseCase preferencias) {
        this.identificar = identificar;
        this.consultarPerfil = consultarPerfil;
        this.actualizarPerfil = actualizarPerfil;
        this.preferencias = preferencias;
    }

    @GetMapping
    public PerfilResponse obtener(Authentication autenticacion) {
        return PerfilResponse.desde(consultarPerfil.consultarPerfil(usuarioId(autenticacion)));
    }

    @PatchMapping
    public PerfilResponse actualizar(Authentication autenticacion,
                                     @Valid @RequestBody ActualizarPerfilRequest request) {
        return PerfilResponse.desde(
                actualizarPerfil.actualizarPerfil(usuarioId(autenticacion), request.aComando()));
    }

    @GetMapping("/preferencias")
    public PreferenciasResponse obtenerPreferencias(Authentication autenticacion) {
        return PreferenciasResponse.desde(preferencias.consultarPreferencias(usuarioId(autenticacion)));
    }

    @PutMapping("/preferencias")
    public PreferenciasResponse reemplazarPreferencias(Authentication autenticacion,
                                                       @Valid @RequestBody PreferenciasRequest request) {
        return PreferenciasResponse.desde(
                preferencias.reemplazarPreferencias(usuarioId(autenticacion), request.aDominio()));
    }

    private UUID usuarioId(Authentication autenticacion) {
        return identificar.identificar(autenticacion.getName());
    }
}