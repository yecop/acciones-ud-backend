package co.edu.udistrital.accionesud.auth.application.service;

import co.edu.udistrital.accionesud.auth.application.dto.ContextoIntento;
import co.edu.udistrital.accionesud.auth.application.dto.PoliticaAutenticacion;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoLogin;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoTokens;
import co.edu.udistrital.accionesud.auth.application.port.in.CerrarSesionUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.IniciarSesionUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.ReenviarMfaUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.RefrescarTokenUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.VerificarMfaUseCase;
import co.edu.udistrital.accionesud.auth.application.port.out.AuditoriaAuthPort;
import co.edu.udistrital.accionesud.auth.application.port.out.GeneradorCodigoMfaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.GeneradorTokenPort;
import co.edu.udistrital.accionesud.auth.application.port.out.HasheadorPort;
import co.edu.udistrital.accionesud.auth.application.port.out.MfaRepositoryPort;
import co.edu.udistrital.accionesud.auth.application.port.out.NotificadorMfaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.RefreshTokenRepositoryPort;
import co.edu.udistrital.accionesud.auth.application.port.out.RelojPort;
import co.edu.udistrital.accionesud.auth.application.port.out.TokenJwtPort;
import co.edu.udistrital.accionesud.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.udistrital.accionesud.auth.application.port.out.VerificadorContrasenaPort;
import co.edu.udistrital.accionesud.auth.domain.exception.CredencialesInvalidasException;
import co.edu.udistrital.accionesud.auth.domain.exception.DemasiadasSolicitudesException;
import co.edu.udistrital.accionesud.auth.domain.exception.MfaExpiradoException;
import co.edu.udistrital.accionesud.auth.domain.exception.MfaInvalidoException;
import co.edu.udistrital.accionesud.auth.domain.model.CanalMfa;
import co.edu.udistrital.accionesud.auth.domain.model.DesafioMfa;
import co.edu.udistrital.accionesud.auth.domain.model.RegistroIntento;
import co.edu.udistrital.accionesud.auth.domain.model.ResultadoIntento;
import co.edu.udistrital.accionesud.auth.domain.model.TipoIntento;
import co.edu.udistrital.accionesud.auth.domain.model.TokenRefresco;
import co.edu.udistrital.accionesud.auth.domain.model.Usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ServicioAutenticacion implements
        IniciarSesionUseCase,
        VerificarMfaUseCase,
        ReenviarMfaUseCase,
        RefrescarTokenUseCase,
        CerrarSesionUseCase {

    private final UsuarioRepositoryPort usuarios;
    private final MfaRepositoryPort desafios;
    private final RefreshTokenRepositoryPort refreshTokens;
    private final NotificadorMfaPort notificador;
    private final GeneradorCodigoMfaPort generadorCodigo;
    private final GeneradorTokenPort generadorToken;
    private final HasheadorPort hasheador;
    private final VerificadorContrasenaPort verificadorContrasena;
    private final TokenJwtPort tokenJwt;
    private final RelojPort reloj;
    private final AuditoriaAuthPort auditoria;
    private final PoliticaAutenticacion politica;
    private final String contrasenaFicticiaHash;

    public ServicioAutenticacion(
            UsuarioRepositoryPort usuarios,
            MfaRepositoryPort desafios,
            RefreshTokenRepositoryPort refreshTokens,
            NotificadorMfaPort notificador,
            GeneradorCodigoMfaPort generadorCodigo,
            GeneradorTokenPort generadorToken,
            HasheadorPort hasheador,
            VerificadorContrasenaPort verificadorContrasena,
            TokenJwtPort tokenJwt,
            RelojPort reloj,
            AuditoriaAuthPort auditoria,
            PoliticaAutenticacion politica,
            String contrasenaFicticiaHash) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.desafios = Objects.requireNonNull(desafios, "desafios");
        this.refreshTokens = Objects.requireNonNull(refreshTokens, "refreshTokens");
        this.notificador = Objects.requireNonNull(notificador, "notificador");
        this.generadorCodigo = Objects.requireNonNull(generadorCodigo, "generadorCodigo");
        this.generadorToken = Objects.requireNonNull(generadorToken, "generadorToken");
        this.hasheador = Objects.requireNonNull(hasheador, "hasheador");
        this.verificadorContrasena =
                Objects.requireNonNull(verificadorContrasena, "verificadorContrasena");
        this.tokenJwt = Objects.requireNonNull(tokenJwt, "tokenJwt");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.politica = Objects.requireNonNull(politica, "politica");
        this.contrasenaFicticiaHash =
                Objects.requireNonNull(contrasenaFicticiaHash, "contrasenaFicticiaHash");
    }

    @Override
    public ResultadoLogin iniciarSesion(String correo, String contrasena, ContextoIntento contexto) {
        Instant ahora = reloj.ahora();
        String correoNormalizado = normalizar(correo);

        int fallos = auditoria.contarFallosLogin(
                correoNormalizado, contexto.ip(), ahora.minus(politica.loginVentana()));
        if (fallos >= politica.loginMaxFallos()) {
            registrar(null, correoNormalizado, TipoIntento.LOGIN, ResultadoIntento.LIMITADO,
                    "límite de intentos de login", contexto, ahora);
            throw new DemasiadasSolicitudesException(politica.loginVentana().toSeconds());
        }

        Optional<Usuario> usuarioOpt = usuarios.buscarPorCorreo(correoNormalizado);
        String hash = usuarioOpt.map(Usuario::contrasenaHash).orElse(contrasenaFicticiaHash);
        boolean contrasenaValida = verificadorContrasena.coincide(contrasena, hash);

        if (usuarioOpt.isEmpty() || !contrasenaValida || !usuarioOpt.get().activo()) {
            registrar(usuarioOpt.map(Usuario::id).orElse(null), correoNormalizado,
                    TipoIntento.LOGIN, ResultadoIntento.FALLIDO, "credenciales inválidas",
                    contexto, ahora);
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = usuarioOpt.get();
        String codigo = generadorCodigo.generar();
        String mfaToken = generadorToken.generar();
        Instant expiraEn = ahora.plus(politica.mfaExpiracion());

        DesafioMfa desafio = new DesafioMfa(
                UUID.randomUUID(),
                usuario.id(),
                hasheador.hash(mfaToken),
                hasheador.hash(codigo),
                CanalMfa.EMAIL,
                ahora,
                expiraEn,
                0,
                politica.mfaMaxIntentos(),
                null,
                0,
                politica.maxReenvios(),
                ahora,
                null);
        desafios.guardar(desafio);
        notificador.enviarCodigo(usuario.correo(), codigo, expiraEn);

        registrar(usuario.id(), usuario.correo(), TipoIntento.LOGIN, ResultadoIntento.EXITOSO,
                "primer factor validado", contexto, ahora);
        registrar(usuario.id(), usuario.correo(), TipoIntento.MFA_ENVIO, ResultadoIntento.EXITOSO,
                "código enviado", contexto, ahora);

        return new ResultadoLogin(mfaToken, CanalMfa.EMAIL.name(),
                politica.mfaExpiracion().toSeconds());
    }

    @Override
    public ResultadoTokens verificarMfa(String mfaToken, String codigo, ContextoIntento contexto) {
        Instant ahora = reloj.ahora();
        DesafioMfa desafio = desafios.buscarPorTokenHash(hasheador.hash(mfaToken))
                .orElseThrow(() -> {
                    registrar(null, null, TipoIntento.MFA_VERIFICACION, ResultadoIntento.FALLIDO,
                            "desafío MFA inexistente", contexto, ahora);
                    return new MfaInvalidoException();
                });

        if (desafio.estaUsado()) {
            registrar(desafio.usuarioId(), null, TipoIntento.MFA_VERIFICACION,
                    ResultadoIntento.FALLIDO, "desafío MFA ya usado", contexto, ahora);
            throw new MfaInvalidoException();
        }

        if (desafio.estaBloqueado(ahora)) {
            registrar(desafio.usuarioId(), null, TipoIntento.MFA_VERIFICACION,
                    ResultadoIntento.BLOQUEADO, "desafío MFA bloqueado", contexto, ahora);
            throw new DemasiadasSolicitudesException(segundosHasta(ahora, desafio.bloqueadoHasta()));
        }

        if (desafio.estaExpirado(ahora)) {
            registrar(desafio.usuarioId(), null, TipoIntento.MFA_VERIFICACION,
                    ResultadoIntento.EXPIRADO, "código MFA expirado", contexto, ahora);
            throw new MfaExpiradoException();
        }

        if (!hasheador.coincide(codigo, desafio.codigoHash())) {
            int intentos = desafio.intentos() + 1;
            if (intentos >= desafio.maxIntentos()) {
                Instant bloqueo = ahora.plus(politica.mfaBloqueo());
                desafios.actualizar(desafio.conIntentoFallido(intentos, bloqueo));
                registrar(desafio.usuarioId(), null, TipoIntento.MFA_VERIFICACION,
                        ResultadoIntento.BLOQUEADO, "máximo de intentos MFA", contexto, ahora);
                throw new DemasiadasSolicitudesException(politica.mfaBloqueo().toSeconds());
            }
            desafios.actualizar(desafio.conIntentoFallido(intentos, null));
            registrar(desafio.usuarioId(), null, TipoIntento.MFA_VERIFICACION,
                    ResultadoIntento.FALLIDO, "código MFA incorrecto", contexto, ahora);
            throw new MfaInvalidoException();
        }

        desafios.actualizar(desafio.usado(ahora));
        Usuario usuario = usuarios.buscarPorId(desafio.usuarioId())
                .filter(Usuario::activo)
                .orElseThrow(CredencialesInvalidasException::new);

        ResultadoTokens resultado = emitirTokens(usuario, ahora, UUID.randomUUID());
        registrar(usuario.id(), usuario.correo(), TipoIntento.MFA_VERIFICACION,
                ResultadoIntento.EXITOSO, "segundo factor validado", contexto, ahora);
        return resultado;
    }

    @Override
    public void reenviarMfa(String mfaToken, ContextoIntento contexto) {
        Instant ahora = reloj.ahora();
        DesafioMfa desafio = desafios.buscarPorTokenHash(hasheador.hash(mfaToken))
                .orElseThrow(MfaInvalidoException::new);

        if (desafio.estaUsado()) {
            throw new MfaInvalidoException();
        }
        if (desafio.estaBloqueado(ahora)) {
            throw new DemasiadasSolicitudesException(segundosHasta(ahora, desafio.bloqueadoHasta()));
        }
        if (desafio.reenvios() >= desafio.maxReenvios()) {
            registrar(desafio.usuarioId(), null, TipoIntento.MFA_REENVIO,
                    ResultadoIntento.LIMITADO, "máximo de reenvíos MFA", contexto, ahora);
            throw new DemasiadasSolicitudesException(politica.reenvioEspera().toSeconds());
        }
        if (desafio.ultimoEnvioEn() != null) {
            Instant disponibleEn = desafio.ultimoEnvioEn().plus(politica.reenvioEspera());
            if (ahora.isBefore(disponibleEn)) {
                registrar(desafio.usuarioId(), null, TipoIntento.MFA_REENVIO,
                        ResultadoIntento.LIMITADO, "espera entre reenvíos MFA", contexto, ahora);
                throw new DemasiadasSolicitudesException(segundosHasta(ahora, disponibleEn));
            }
        }

        String codigo = generadorCodigo.generar();
        Instant expiraEn = ahora.plus(politica.mfaExpiracion());
        DesafioMfa actualizado = desafio.reenviado(hasheador.hash(codigo), expiraEn, ahora);
        desafios.actualizar(actualizado);

        Usuario usuario = usuarios.buscarPorId(desafio.usuarioId())
                .orElseThrow(MfaInvalidoException::new);
        notificador.enviarCodigo(usuario.correo(), codigo, expiraEn);
        registrar(usuario.id(), usuario.correo(), TipoIntento.MFA_REENVIO,
                ResultadoIntento.EXITOSO, "código reenviado", contexto, ahora);
    }

    @Override
    public ResultadoTokens refrescarToken(String refreshToken, ContextoIntento contexto) {
        Instant ahora = reloj.ahora();
        String tokenHash = hasheador.hash(refreshToken);
        TokenRefresco token = refreshTokens.buscarPorHash(tokenHash)
                .orElseThrow(() -> {
                    registrar(null, null, TipoIntento.REFRESH, ResultadoIntento.FALLIDO,
                            "refresh token inexistente", contexto, ahora);
                    return new CredencialesInvalidasException();
                });

        if (token.estaUsado() || token.estaRevocado()) {
            refreshTokens.revocarFamilia(token.familiaId(), ahora);
            registrar(token.usuarioId(), null, TipoIntento.REFRESH, ResultadoIntento.FALLIDO,
                    "reutilización de refresh token", contexto, ahora);
            throw new CredencialesInvalidasException();
        }

        if (token.estaExpirado(ahora)) {
            refreshTokens.revocarFamilia(token.familiaId(), ahora);
            registrar(token.usuarioId(), null, TipoIntento.REFRESH, ResultadoIntento.EXPIRADO,
                    "refresh token expirado", contexto, ahora);
            throw new CredencialesInvalidasException();
        }

        if (!refreshTokens.marcarUsado(tokenHash, ahora)) {
            refreshTokens.revocarFamilia(token.familiaId(), ahora);
            registrar(token.usuarioId(), null, TipoIntento.REFRESH, ResultadoIntento.FALLIDO,
                    "refresh token usado en paralelo", contexto, ahora);
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = usuarios.buscarPorId(token.usuarioId())
                .filter(Usuario::activo)
                .orElseThrow(CredencialesInvalidasException::new);

        ResultadoTokens resultado = emitirTokens(usuario, ahora, token.familiaId());
        registrar(usuario.id(), usuario.correo(), TipoIntento.REFRESH,
                ResultadoIntento.EXITOSO, "tokens rotados", contexto, ahora);
        return resultado;
    }

    @Override
    public void cerrarSesion(String refreshToken, UUID usuarioId, ContextoIntento contexto) {
        Instant ahora = reloj.ahora();
        Optional<TokenRefresco> token = refreshTokens.buscarPorHash(hasheador.hash(refreshToken));

        if (token.isPresent() && token.get().usuarioId().equals(usuarioId)) {
            refreshTokens.revocarFamilia(token.get().familiaId(), ahora);
            registrar(usuarioId, null, TipoIntento.LOGOUT, ResultadoIntento.EXITOSO,
                    "sesión cerrada", contexto, ahora);
            return;
        }

        registrar(usuarioId, null, TipoIntento.LOGOUT, ResultadoIntento.FALLIDO,
                "refresh token no revocable", contexto, ahora);
    }

    private ResultadoTokens emitirTokens(Usuario usuario, Instant ahora, UUID familiaId) {
        String accessToken = tokenJwt.emitirAccessToken(usuario, ahora);
        String refreshToken = generadorToken.generar();
        TokenRefresco token = new TokenRefresco(
                UUID.randomUUID(),
                usuario.id(),
                hasheador.hash(refreshToken),
                familiaId,
                ahora,
                ahora.plus(politica.refreshExpiracion()),
                null,
                null);
        refreshTokens.guardar(token);
        return new ResultadoTokens(accessToken, refreshToken, "Bearer",
                tokenJwt.expiracionSegundos(), usuario);
    }

    private void registrar(UUID usuarioId, String correo, TipoIntento tipo,
                           ResultadoIntento resultado, String motivo,
                           ContextoIntento contexto, Instant ahora) {
        auditoria.registrar(new RegistroIntento(
                usuarioId,
                correo,
                tipo,
                resultado,
                motivo,
                contexto.ip(),
                contexto.userAgent(),
                contexto.traceId(),
                ahora));
    }

    private String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    private long segundosHasta(Instant desde, Instant hasta) {
        return Math.max(1, Duration.between(desde, hasta).toSeconds());
    }
}
