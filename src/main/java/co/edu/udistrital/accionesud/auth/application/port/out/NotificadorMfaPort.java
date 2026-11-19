package co.edu.udistrital.accionesud.auth.application.port.out;

import java.time.Instant;

public interface NotificadorMfaPort {

    void enviarCodigo(String correo, String codigo, Instant expiraEn);
}
