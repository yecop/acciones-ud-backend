package co.edu.udistrital.accionesud.auth.application.service;

import co.edu.udistrital.accionesud.auth.application.dto.NuevoUsuario;
import co.edu.udistrital.accionesud.auth.application.dto.RegistrarUsuarioCommand;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoRegistro;
import co.edu.udistrital.accionesud.auth.application.dto.SolicitudAlta;
import co.edu.udistrital.accionesud.auth.application.port.out.AuditoriaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.BrokerPort;
import co.edu.udistrital.accionesud.auth.application.port.out.HashContrasenaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.udistrital.accionesud.auth.domain.exception.BrokerNoDisponibleException;
import co.edu.udistrital.accionesud.auth.domain.exception.CorreoDuplicadoException;
import co.edu.udistrital.accionesud.auth.domain.model.EstadoUsuario;
import co.edu.udistrital.accionesud.auth.domain.model.NivelExperiencia;
import co.edu.udistrital.accionesud.auth.domain.model.NivelRiesgo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    @Mock UsuarioRepositoryPort usuarios;
    @Mock HashContrasenaPort hash;
    @Mock BrokerPort broker;
    @Mock AuditoriaPort auditoria;
    @InjectMocks RegistroService servicio;

    private RegistrarUsuarioCommand comando() {
        return new RegistrarUsuarioCommand("Ana", "Perez", "  Ana@Example.test ", "Secreta123",
                "+573001112233", LocalDate.of(1999, 5, 20), NivelExperiencia.PRINCIPIANTE,
                NivelRiesgo.MEDIO, "Ahorrar", Set.of("NASDAQ"), Set.of("Finanzas"));
    }

    @Test
    void registroExitosoCreaCuentaYQuedaActivo() {
        when(usuarios.existeCorreo("ana@example.test")).thenReturn(false);
        when(hash.hashear("Secreta123")).thenReturn("{bcrypt}hash");
        when(broker.crearCuenta(any())).thenReturn("alpaca-1");

        ResultadoRegistro r = servicio.registrar(comando());

        assertEquals(EstadoUsuario.ACTIVO, r.estado());
        ArgumentCaptor<NuevoUsuario> captor = ArgumentCaptor.forClass(NuevoUsuario.class);
        verify(usuarios).registrarPendiente(captor.capture());
        assertEquals("{bcrypt}hash", captor.getValue().contrasenaHash());
        assertEquals("ana@example.test", captor.getValue().correo());
        verify(usuarios).marcarAltaCompleta(r.usuarioId(), "alpaca-1");
    }

    @Test
    void correoDuplicadoNoGuardaNiLlamaAlBroker() {
        when(usuarios.existeCorreo("ana@example.test")).thenReturn(true);

        assertThrows(CorreoDuplicadoException.class, () -> servicio.registrar(comando()));

        verify(usuarios, never()).registrarPendiente(any());
        verifyNoInteractions(broker, hash);
    }

    @Test
    void siAlpacaFallaElUsuarioQuedaPendiente() {
        when(usuarios.existeCorreo("ana@example.test")).thenReturn(false);
        when(hash.hashear("Secreta123")).thenReturn("{bcrypt}hash");
        when(broker.crearCuenta(any())).thenThrow(new BrokerNoDisponibleException("HTTP 503", null));

        ResultadoRegistro r = servicio.registrar(comando());

        assertEquals(EstadoUsuario.PENDIENTE_ALTA, r.estado());
        verify(usuarios).registrarPendiente(any());
        verify(usuarios, never()).marcarAltaCompleta(any(), anyString());
        verify(auditoria).registrar(eq("ALTA_BROKER_PENDIENTE"), eq(r.usuarioId()), anyMap());
    }

    @Test
    void reintentoCompletaSoloLasQueAlpacaAcepta() {
        SolicitudAlta s1 = new SolicitudAlta(UUID.randomUUID(), "Ana", "Perez", "a@example.test", null,
                LocalDate.of(1999, 5, 20));
        SolicitudAlta s2 = new SolicitudAlta(UUID.randomUUID(), "Luis", "Gomez", "l@example.test", null,
                LocalDate.of(1998, 1, 2));
        when(usuarios.buscarAltasPendientes(10)).thenReturn(List.of(s1, s2));
        when(broker.crearCuenta(s1)).thenReturn("a1");
        when(broker.crearCuenta(s2)).thenThrow(new BrokerNoDisponibleException("HTTP 503", null));

        assertEquals(1, servicio.reintentar(10));

        verify(usuarios).marcarAltaCompleta(s1.usuarioId(), "a1");
        verify(usuarios, never()).marcarAltaCompleta(eq(s2.usuarioId()), anyString());
    }
}