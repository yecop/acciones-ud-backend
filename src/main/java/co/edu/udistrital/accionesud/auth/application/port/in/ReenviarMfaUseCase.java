package co.edu.udistrital.accionesud.auth.application.port.in;

import co.edu.udistrital.accionesud.auth.application.dto.ContextoIntento;

public interface ReenviarMfaUseCase {

    void reenviarMfa(String mfaToken, ContextoIntento contexto);
}
