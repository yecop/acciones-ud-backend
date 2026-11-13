package co.edu.udistrital.accionesud.auth.application.port.out;

import co.edu.udistrital.accionesud.auth.domain.model.Usuario;

import java.time.Instant;

public interface TokenJwtPort {

    String emitirAccessToken(Usuario usuario, Instant ahora);

    long expiracionSegundos();
}
