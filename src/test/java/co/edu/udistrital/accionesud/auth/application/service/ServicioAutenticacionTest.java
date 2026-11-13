package co.edu.udistrital.accionesud.auth.application.service;

import co.edu.udistrital.accionesud.auth.application.dto.ContextoIntento;
import co.edu.udistrital.accionesud.auth.application.dto.PoliticaAutenticacion;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoLogin;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoTokens;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicioAutenticacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-12T12:00:00Z");
    private static final String CORREO = "inversionista@accionesud.local";
    private static final String CLAVE = "Clave123!";
    private static final UUID USUARIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FAMILIA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String HASH_FICTICIO = "{bcrypt}hash-ficticio";

    @Mock
    private UsuarioRepositoryPort usuarios;
    @Mock
    private MfaRepositoryPort desafios;
    @Mock
    private RefreshTokenRepositoryPort refreshTokens;
    @Mock
    private NotificadorMfaPort notificador;
    @Mock
    private GeneradorCodigoMfaPort generadorCodigo;
    @Mock
    private GeneradorTokenPort generadorToken;
    @Mock
    private HasheadorPort hasheador;
    @Mock
    private VerificadorContrasenaPort verificadorContrasena;
    @Mock
    private TokenJwtPort tokenJwt;
    @Mock
    private RelojPort reloj;
    @Mock
    private AuditoriaAuthPort auditoria;

    private ServicioAutenticacion servicio;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        PoliticaAutenticacion politica = new PoliticaAutenticacion(
                Duration.ofMinutes(5), 5, Duration.ofMinutes(15), Duration.ofSeconds(60),
                3, Duration.ofDays(7), 5, Duration.ofMinutes(15));
        servicio = new ServicioAutenticacion(
                usuarios, desafios, refreshTokens, notificador, generadorCodigo, generadorToken,
                hasheador, verificadorContrasena, tokenJwt, reloj, auditoria, politica,
                HASH_FICTICIO);
        usuario = new Usuario(USUARIO_ID, "Inversionista", CORREO, "{bcrypt}clave",
                "INVERSIONISTA", true);
        when(reloj.ahora()).thenReturn(AHORA);
    }

    @Test
    void loginValidoCreaDesafioYEnviaCodigo() {
        ContextoIntento contexto = contexto();
        when(auditoria.contarFallosLogin(eq(CORREO), eq("127.0.0.1"), any())).thenReturn(0);
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(verificadorContrasena.coincide(CLAVE, usuario.contrasenaHash())).thenReturn(true);
        when(generadorCodigo.generar()).thenReturn("123456");
        when(generadorToken.generar()).thenReturn("mfa-token");
        when(hasheador.hash(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));

        ResultadoLogin resultado = servicio.iniciarSesion(CORREO, CLAVE, contexto);

        assertThat(resultado.mfaToken()).isEqualTo("mfa-token");
        assertThat(resultado.canal()).isEqualTo("EMAIL");
        assertThat(resultado.expiraEn()).isEqualTo(300);

        ArgumentCaptor<DesafioMfa> desafio = ArgumentCaptor.forClass(DesafioMfa.class);
        verify(desafios).guardar(desafio.capture());
        assertThat(desafio.getValue().usuarioId()).isEqualTo(USUARIO_ID);
        assertThat(desafio.getValue().tokenHash()).isEqualTo("hash:mfa-token");
        assertThat(desafio.getValue().codigoHash()).isEqualTo("hash:123456");
        assertThat(desafio.getValue().expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        assertThat(desafio.getValue().maxIntentos()).isEqualTo(5);

        verify(notificador).enviarCodigo(CORREO, "123456", AHORA.plusSeconds(300));
        verify(auditoria, times(2)).registrar(any(RegistroIntento.class));
    }

    @Test
    void loginConCorreoDesconocidoUsaHashFicticioYNoCreaDesafio() {
        ContextoIntento contexto = contexto();
        when(auditoria.contarFallosLogin(anyString(), anyString(), any())).thenReturn(0);
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.empty());
        when(verificadorContrasena.coincide(CLAVE, HASH_FICTICIO)).thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion(CORREO, CLAVE, contexto))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales inválidas");

        verify(verificadorContrasena).coincide(CLAVE, HASH_FICTICIO);
        verify(desafios, never()).guardar(any());
        verify(notificador, never()).enviarCodigo(anyString(), anyString(), any());
    }

    @Test
    void loginBloqueaCuandoSuperaElLimiteDeFallos() {
        when(auditoria.contarFallosLogin(eq(CORREO), eq("127.0.0.1"), any())).thenReturn(5);

        assertThatThrownBy(() -> servicio.iniciarSesion(CORREO, CLAVE, contexto()))
                .isInstanceOf(DemasiadasSolicitudesException.class);

        verify(verificadorContrasena, never()).coincide(anyString(), anyString());
        verify(desafios, never()).guardar(any());
    }

    @Test
    void verificarMfaValidoEmiteTokensYGuardaRefresh() {
        DesafioMfa desafio = desafio(AHORA.plusSeconds(300), 0, null);
        when(hasheador.hash("mfa-token")).thenReturn("hash:mfa-token");
        when(hasheador.coincide("123456", "hash:123456")).thenReturn(true);
        when(desafios.buscarPorTokenHash("hash:mfa-token")).thenReturn(Optional.of(desafio));
        when(usuarios.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(tokenJwt.emitirAccessToken(usuario, AHORA)).thenReturn("access-token");
        when(tokenJwt.expiracionSegundos()).thenReturn(900L);
        when(generadorToken.generar()).thenReturn("refresh-token");
        when(hasheador.hash("refresh-token")).thenReturn("hash:refresh-token");

        ResultadoTokens resultado = servicio.verificarMfa("mfa-token", "123456", contexto());

        assertThat(resultado.accessToken()).isEqualTo("access-token");
        assertThat(resultado.refreshToken()).isEqualTo("refresh-token");
        assertThat(resultado.tokenType()).isEqualTo("Bearer");
        assertThat(resultado.expiraEn()).isEqualTo(900);
        assertThat(resultado.usuario()).isEqualTo(usuario);

        verify(desafios).actualizar(desafio.usado(AHORA));
        ArgumentCaptor<TokenRefresco> token = ArgumentCaptor.forClass(TokenRefresco.class);
        verify(refreshTokens).guardar(token.capture());
        assertThat(token.getValue().usuarioId()).isEqualTo(USUARIO_ID);
        assertThat(token.getValue().tokenHash()).isEqualTo("hash:refresh-token");
        assertThat(token.getValue().expiraEn()).isEqualTo(AHORA.plus(Duration.ofDays(7)));
    }

    @Test
    void verificarMfaIncorrectoIncrementaIntentosSinEmitirTokens() {
        DesafioMfa desafio = desafio(AHORA.plusSeconds(300), 1, null);
        when(hasheador.hash("mfa-token")).thenReturn("hash:mfa-token");
        when(hasheador.coincide("000000", "hash:123456")).thenReturn(false);
        when(desafios.buscarPorTokenHash("hash:mfa-token")).thenReturn(Optional.of(desafio));

        assertThatThrownBy(() -> servicio.verificarMfa("mfa-token", "000000", contexto()))
                .isInstanceOf(MfaInvalidoException.class);

        ArgumentCaptor<DesafioMfa> actualizado = ArgumentCaptor.forClass(DesafioMfa.class);
        verify(desafios).actualizar(actualizado.capture());
        assertThat(actualizado.getValue().intentos()).isEqualTo(2);
        assertThat(actualizado.getValue().bloqueadoHasta()).isNull();
        verify(tokenJwt, never()).emitirAccessToken(any(), any());
        verify(refreshTokens, never()).guardar(any());
    }

    @Test
    void verificarMfaExpiradoNoActualizaElDesafio() {
        DesafioMfa desafio = desafio(AHORA, 0, null);
        when(hasheador.hash("mfa-token")).thenReturn("hash:mfa-token");
        when(desafios.buscarPorTokenHash("hash:mfa-token")).thenReturn(Optional.of(desafio));

        assertThatThrownBy(() -> servicio.verificarMfa("mfa-token", "123456", contexto()))
                .isInstanceOf(MfaExpiradoException.class);

        verify(desafios, never()).actualizar(any());
        verify(tokenJwt, never()).emitirAccessToken(any(), any());
    }

    @Test
    void refreshValidoRotaTokenDentroDeLaMismaFamilia() {
        TokenRefresco token = refresh(AHORA.plus(Duration.ofDays(1)), null, null);
        when(hasheador.hash("refresh-viejo")).thenReturn("hash:refresh-viejo");
        when(hasheador.hash("refresh-nuevo")).thenReturn("hash:refresh-nuevo");
        when(refreshTokens.buscarPorHash("hash:refresh-viejo")).thenReturn(Optional.of(token));
        when(refreshTokens.marcarUsado("hash:refresh-viejo", AHORA)).thenReturn(true);
        when(usuarios.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(tokenJwt.emitirAccessToken(usuario, AHORA)).thenReturn("access-token");
        when(tokenJwt.expiracionSegundos()).thenReturn(900L);
        when(generadorToken.generar()).thenReturn("refresh-nuevo");

        ResultadoTokens resultado = servicio.refrescarToken("refresh-viejo", contexto());

        assertThat(resultado.refreshToken()).isEqualTo("refresh-nuevo");
        ArgumentCaptor<TokenRefresco> nuevo = ArgumentCaptor.forClass(TokenRefresco.class);
        verify(refreshTokens).guardar(nuevo.capture());
        assertThat(nuevo.getValue().familiaId()).isEqualTo(FAMILIA_ID);
        verify(refreshTokens, never()).revocarFamilia(any(), any());
    }

    @Test
    void refreshReutilizadoRevocaTodaLaFamilia() {
        TokenRefresco token = refresh(AHORA.plus(Duration.ofDays(1)), AHORA.minusSeconds(30), null);
        when(hasheador.hash("refresh-viejo")).thenReturn("hash:refresh-viejo");
        when(refreshTokens.buscarPorHash("hash:refresh-viejo")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> servicio.refrescarToken("refresh-viejo", contexto()))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(refreshTokens).revocarFamilia(FAMILIA_ID, AHORA);
        verify(usuarios, never()).buscarPorId(any());
    }

    @Test
    void logoutConTokenDeOtroUsuarioNoRevocaLaFamilia() {
        UUID otroUsuario = UUID.randomUUID();
        TokenRefresco token = new TokenRefresco(UUID.randomUUID(), otroUsuario,
                "hash:refresh", FAMILIA_ID, AHORA.minusSeconds(10),
                AHORA.plus(Duration.ofDays(1)), null, null);
        when(hasheador.hash("refresh")).thenReturn("hash:refresh");
        when(refreshTokens.buscarPorHash("hash:refresh")).thenReturn(Optional.of(token));

        servicio.cerrarSesion("refresh", USUARIO_ID, contexto());

        verify(refreshTokens, never()).revocarFamilia(any(), any());
        ArgumentCaptor<RegistroIntento> intento = ArgumentCaptor.forClass(RegistroIntento.class);
        verify(auditoria).registrar(intento.capture());
        assertThat(intento.getValue().resultado()).isEqualTo(ResultadoIntento.FALLIDO);
    }

    @Test
    void reenviarMfaRespetaLaEsperaEntreEnvios() {
        DesafioMfa desafio = desafio(AHORA.plusSeconds(300), 0, AHORA.minusSeconds(10));
        when(hasheador.hash("mfa-token")).thenReturn("hash:mfa-token");
        when(desafios.buscarPorTokenHash("hash:mfa-token")).thenReturn(Optional.of(desafio));

        assertThatThrownBy(() -> servicio.reenviarMfa("mfa-token", contexto()))
                .isInstanceOf(DemasiadasSolicitudesException.class)
                .extracting("reintentarEnSegundos")
                .isEqualTo(50L);

        verify(notificador, never()).enviarCodigo(anyString(), anyString(), any());
    }

    private DesafioMfa desafio(Instant expiraEn, int intentos, Instant ultimoEnvioEn) {
        return new DesafioMfa(
                UUID.randomUUID(), USUARIO_ID, "hash:mfa-token", "hash:123456", CanalMfa.EMAIL,
                AHORA.minusSeconds(10), expiraEn, intentos, 5, null, 0, 3,
                ultimoEnvioEn, null);
    }

    private TokenRefresco refresh(Instant expiraEn, Instant usadoEn, Instant revocadoEn) {
        return new TokenRefresco(UUID.randomUUID(), USUARIO_ID, "hash:refresh-viejo", FAMILIA_ID,
                AHORA.minusSeconds(60), expiraEn, usadoEn, revocadoEn);
    }

    private ContextoIntento contexto() {
        return new ContextoIntento("trace-1", "127.0.0.1", "JUnit");
    }
}
