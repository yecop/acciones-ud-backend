package co.edu.udistrital.accionesud.auth.application.port.in;

import co.edu.udistrital.accionesud.auth.application.dto.RegistrarUsuarioCommand;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoRegistro;

public interface RegistrarUsuarioUseCase {

    ResultadoRegistro registrar(RegistrarUsuarioCommand comando);
}