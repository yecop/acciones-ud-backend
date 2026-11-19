package co.edu.udistrital.accionesud.auth.application.port.in;

import co.edu.udistrital.accionesud.auth.application.dto.ContextoIntento;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoTokens;

public interface RefrescarTokenUseCase {

    ResultadoTokens refrescarToken(String refreshToken, ContextoIntento contexto);
}
