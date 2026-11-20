package co.edu.udistrital.accionesud.perfil.application.service;

import co.edu.udistrital.accionesud.perfil.application.dto.ActualizarPerfilCommand;
import co.edu.udistrital.accionesud.perfil.application.port.out.PerfilRepositoryPort;
import co.edu.udistrital.accionesud.perfil.domain.exception.PerfilNoEncontradoException;
import co.edu.udistrital.accionesud.perfil.domain.exception.UsuarioNoEncontradoException;
import co.edu.udistrital.accionesud.perfil.domain.model.CanalNotificacion;
import co.edu.udistrital.accionesud.perfil.domain.model.EventoNotificacion;
import co.edu.udistrital.accionesud.perfil.domain.model.OrdenPortafolio;
import co.edu.udistrital.accionesud.perfil.domain.model.PerfilUsuario;
import co.edu.udistrital.accionesud.perfil.domain.model.PreferenciasPerfil;
import co.edu.udistrital.accionesud.perfil.domain.model.TipoOrden;
import co.edu.udistrital.accionesud.perfil.domain.model.TipoVisualizacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock PerfilRepositoryPort repo;
    @InjectMocks PerfilService servicio;

    private PreferenciasPerfil preferencias() {
        return new PreferenciasPerfil(TipoVisualizacion.BARRAS, OrdenPortafolio.PORCENTAJE_GANANCIA, true,
                TipoOrden.LIMIT, 10, false, null, true, EnumSet.of(CanalNotificacion.SMS),
                EnumSet.of(EventoNotificacion.ORDEN_EJECUTADA));
    }

    @Test
    void identificaPorUuidSinConsultarLaBase() {
        UUID id = UUID.randomUUID();
        assertEquals(id, servicio.identificar(id.toString()));
        verifyNoInteractions(repo);
    }

    @Test
    void identificaPorCorreoNormalizado() {
        UUID id = UUID.randomUUID();
        when(repo.buscarIdPorCorreo("ana@example.test")).thenReturn(Optional.of(id));
        assertEquals(id, servicio.identificar("  Ana@Example.test "));
    }

    @Test
    void usuarioInexistenteLanzaExcepcion() {
        when(repo.buscarIdPorCorreo("x@example.test")).thenReturn(Optional.empty());
        assertThrows(UsuarioNoEncontradoException.class, () -> servicio.identificar("x@example.test"));
    }

    @Test
    void preferenciasSinGuardarDevuelvenLosValoresPorDefecto() {
        UUID id = UUID.randomUUID();
        when(repo.existeInversionista(id)).thenReturn(true);
        when(repo.buscarPreferencias(id)).thenReturn(Optional.empty());
        assertEquals(PreferenciasPerfil.porDefecto(), servicio.consultarPreferencias(id));
    }

    @Test
    void preferenciasDeUnUsuarioSinPerfilDeInversionistaLanza404() {
        UUID id = UUID.randomUUID();
        when(repo.existeInversionista(id)).thenReturn(false);
        assertThrows(PerfilNoEncontradoException.class, () -> servicio.consultarPreferencias(id));
        verify(repo, never()).guardarPreferencias(any(), any());
    }

    @Test
    void reemplazarGuardaYDevuelveLoPersistido() {
        UUID id = UUID.randomUUID();
        PreferenciasPerfil p = preferencias();
        when(repo.existeInversionista(id)).thenReturn(true);
        when(repo.buscarPreferencias(id)).thenReturn(Optional.of(p));

        assertEquals(p, servicio.reemplazarPreferencias(id, p));
        verify(repo).guardarPreferencias(id, p);
    }

    @Test
    void actualizarPerfilVerificaActualizaYRelee() {
        UUID id = UUID.randomUUID();
        PerfilUsuario perfil = new PerfilUsuario(id, "Ana", "Perez", "ana@example.test", null,
                LocalDate.of(1999, 5, 20), "ACTIVO", "PRINCIPIANTE", "MEDIO", null, List.of(), List.of());
        ActualizarPerfilCommand comando = new ActualizarPerfilCommand("Ana María", null, null, null, null, null, null);
        when(repo.actualizarPerfil(id, comando)).thenReturn(Optional.of(perfil));

        assertEquals(perfil, servicio.actualizarPerfil(id, comando));
        verify(repo).actualizarPerfil(id, comando);
    }

    @Test
    void actualizarPerfilInexistenteLanza404() {
        UUID id = UUID.randomUUID();
        ActualizarPerfilCommand comando = new ActualizarPerfilCommand("Ana María", null, null, null, null, null, null);
        when(repo.actualizarPerfil(id, comando)).thenReturn(Optional.empty());

        assertThrows(PerfilNoEncontradoException.class, () -> servicio.actualizarPerfil(id, comando));
        verify(repo).actualizarPerfil(id, comando);
    }
}
