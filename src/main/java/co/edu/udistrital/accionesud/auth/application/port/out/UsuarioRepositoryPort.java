package co.edu.udistrital.accionesud.auth.application.port.out;

import co.edu.udistrital.accionesud.auth.application.dto.NuevoUsuario;
import co.edu.udistrital.accionesud.auth.application.dto.SolicitudAlta;

import java.util.List;
import java.util.UUID;

public interface UsuarioRepositoryPort {

    boolean existeCorreo(String correo);

    /** Guarda usuario, inversionista, intereses y cuenta de corretaje (sin id de Alpaca) en estado PENDIENTE_ALTA. */
    void registrarPendiente(NuevoUsuario nuevo);

    /** Asocia la cuenta de Alpaca y pasa el usuario a ACTIVO. */
    void marcarAltaCompleta(UUID usuarioId, String cuentaBroker);

    List<SolicitudAlta> buscarAltasPendientes(int limite);
}