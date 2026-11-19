package co.edu.udistrital.accionesud.auth.application.port.in;

import co.edu.udistrital.accionesud.auth.application.dto.ContextoIntento;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoLogin;

public interface IniciarSesionUseCase {

    ResultadoLogin iniciarSesion(String correo, String contrasena, ContextoIntento contexto);
}
