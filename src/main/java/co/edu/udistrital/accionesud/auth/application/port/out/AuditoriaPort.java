package co.edu.udistrital.accionesud.auth.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface AuditoriaPort {

    void registrar(String evento, UUID usuarioId, Map<String, Object> datos);
}