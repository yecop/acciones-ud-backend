package co.edu.udistrital.accionesud.auth.application.service;

import co.edu.udistrital.accionesud.auth.application.dto.NuevoUsuario;
import co.edu.udistrital.accionesud.auth.application.dto.RegistrarUsuarioCommand;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoRegistro;
import co.edu.udistrital.accionesud.auth.application.dto.SolicitudAlta;
import co.edu.udistrital.accionesud.auth.application.port.in.RegistrarUsuarioUseCase;
import co.edu.udistrital.accionesud.auth.application.port.in.ReintentarAltasPendientesUseCase;
import co.edu.udistrital.accionesud.auth.application.port.out.AuditoriaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.BrokerPort;
import co.edu.udistrital.accionesud.auth.application.port.out.HashContrasenaPort;
import co.edu.udistrital.accionesud.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.udistrital.accionesud.auth.domain.exception.BrokerNoDisponibleException;
import co.edu.udistrital.accionesud.auth.domain.exception.CorreoDuplicadoException;
import co.edu.udistrital.accionesud.auth.domain.model.EstadoUsuario;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RegistroService implements RegistrarUsuarioUseCase, ReintentarAltasPendientesUseCase {

    private final UsuarioRepositoryPort usuarios;
    private final HashContrasenaPort hash;
    private final BrokerPort broker;
    private final AuditoriaPort auditoria;

    public RegistroService(UsuarioRepositoryPort usuarios, HashContrasenaPort hash,
                           BrokerPort broker, AuditoriaPort auditoria) {
        this.usuarios = usuarios;
        this.hash = hash;
        this.broker = broker;
        this.auditoria = auditoria;
    }

    @Override
    public ResultadoRegistro registrar(RegistrarUsuarioCommand c) {
        String correo = c.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existeCorreo(correo)) {
            throw new CorreoDuplicadoException();
        }
        UUID usuarioId = UUID.randomUUID();
        String nombres = c.nombres().trim();
        String apellidos = c.apellidos().trim();

        usuarios.registrarPendiente(new NuevoUsuario(
                usuarioId, UUID.randomUUID(), nombres, apellidos, correo,
                hash.hashear(c.contrasena()), c.celular(), c.fechaNacimiento(),
                c.nivelExperiencia(), c.nivelRiesgo(), c.objetivos(), c.mercados(), c.sectores()));
        auditoria.registrar("USUARIO_REGISTRADO", usuarioId,
                Map.of("estado", EstadoUsuario.PENDIENTE_ALTA.name()));

        EstadoUsuario estado = intentarAlta(
                new SolicitudAlta(usuarioId, nombres, apellidos, correo, c.celular(), c.fechaNacimiento()));
        return new ResultadoRegistro(usuarioId, correo, estado);
    }

    @Override
    public int reintentar(int maximo) {
        int completadas = 0;
        for (SolicitudAlta solicitud : usuarios.buscarAltasPendientes(maximo)) {
            if (intentarAlta(solicitud) == EstadoUsuario.ACTIVO) {
                completadas++;
            }
        }
        return completadas;
    }

    private EstadoUsuario intentarAlta(SolicitudAlta solicitud) {
        try {
            String cuentaBroker = broker.crearCuenta(solicitud);
            usuarios.marcarAltaCompleta(solicitud.usuarioId(), cuentaBroker);
            auditoria.registrar("ALTA_BROKER_COMPLETADA", solicitud.usuarioId(),
                    Map.of("cuentaBroker", cuentaBroker));
            return EstadoUsuario.ACTIVO;
        } catch (BrokerNoDisponibleException e) {
            auditoria.registrar("ALTA_BROKER_PENDIENTE", solicitud.usuarioId(),
                    Map.of("motivo", String.valueOf(e.getMessage())));
            return EstadoUsuario.PENDIENTE_ALTA;
        }
    }
}