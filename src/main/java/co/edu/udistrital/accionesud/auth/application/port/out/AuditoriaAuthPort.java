package co.edu.udistrital.accionesud.auth.application.port.out;

import co.edu.udistrital.accionesud.auth.domain.model.RegistroIntento;

import java.time.Instant;

public interface AuditoriaAuthPort {

    void registrar(RegistroIntento intento);

    int contarFallosLogin(String correo, String ip, Instant desde);
}
