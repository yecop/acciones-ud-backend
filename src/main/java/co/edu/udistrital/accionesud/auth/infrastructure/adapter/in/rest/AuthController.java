package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.auth.application.dto.ContextoIntento;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoLogin;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoTokens;
import co.edu.udistrital.accionesud.auth.application.port.in.CerrarSesionUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.IniciarSesionUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.ReenviarMfaUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.RefrescarTokenUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.VerificarMfaUseCase;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto.LoginRequest;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto.LoginResponse;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto.RefreshTokenRequest;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto.ReenviarMfaRequest;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto.TokensResponse;
import co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest.dto.VerificarMfaRequest;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.trace.TraceIdContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Login con MFA, refresh y cierre de sesión")
public class AuthController {

    private final IniciarSesionUseCase iniciarSesion;
    private final VerificarMfaUseCase verificarMfa;
    private final ReenviarMfaUseCase reenviarMfa;
    private final RefrescarTokenUseCase refrescarToken;
    private final CerrarSesionUseCase cerrarSesion;

    public AuthController(
            IniciarSesionUseCase iniciarSesion,
            VerificarMfaUseCase verificarMfa,
            ReenviarMfaUseCase reenviarMfa,
            RefrescarTokenUseCase refrescarToken,
            CerrarSesionUseCase cerrarSesion) {
        this.iniciarSesion = iniciarSesion;
        this.verificarMfa = verificarMfa;
        this.reenviarMfa = reenviarMfa;
        this.refrescarToken = refrescarToken;
        this.cerrarSesion = cerrarSesion;
    }

    @PostMapping("/login")
    @Operation(summary = "Valida las credenciales y envía el código MFA")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest solicitud,
                                               HttpServletRequest request) {
        ResultadoLogin resultado = iniciarSesion.iniciarSesion(
                solicitud.correo(), solicitud.contrasena(), contexto(request));
        return ResponseEntity.ok(LoginResponse.desde(resultado));
    }

    @PostMapping("/mfa/verificar")
    @Operation(summary = "Verifica el código MFA y emite los tokens")
    public ResponseEntity<TokensResponse> verificar(@Valid @RequestBody VerificarMfaRequest solicitud,
                                                    HttpServletRequest request) {
        ResultadoTokens resultado = verificarMfa.verificarMfa(
                solicitud.mfaToken(), solicitud.codigo(), contexto(request));
        return ResponseEntity.ok(TokensResponse.desde(resultado));
    }

    @PostMapping("/mfa/reenviar")
    @Operation(summary = "Reenvía el código MFA")
    public ResponseEntity<Void> reenviar(@Valid @RequestBody ReenviarMfaRequest solicitud,
                                         HttpServletRequest request) {
        reenviarMfa.reenviarMfa(solicitud.mfaToken(), contexto(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rota el refresh token y emite un nuevo access token")
    public ResponseEntity<TokensResponse> refresh(@Valid @RequestBody RefreshTokenRequest solicitud,
                                                  HttpServletRequest request) {
        ResultadoTokens resultado = refrescarToken.refrescarToken(
                solicitud.refreshToken(), contexto(request));
        return ResponseEntity.ok(TokensResponse.desde(resultado));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalida la sesión y revoca el refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest solicitud,
                                       @AuthenticationPrincipal Jwt jwt,
                                       HttpServletRequest request) {
        cerrarSesion.cerrarSesion(
                solicitud.refreshToken(), UUID.fromString(jwt.getSubject()), contexto(request));
        return ResponseEntity.noContent().build();
    }

    private ContextoIntento contexto(HttpServletRequest request) {
        return new ContextoIntento(
                TraceIdContext.desde(request),
                ip(request),
                request.getHeader("User-Agent"));
    }

    private String ip(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
