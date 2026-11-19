package co.edu.udistrital.accionesud.auth.application.port.out;

import co.edu.udistrital.accionesud.auth.application.dto.SolicitudAlta;

public interface BrokerPort {

    /** Crea la cuenta de corretaje y devuelve su id. Lanza BrokerNoDisponibleException si falla o no responde. */
    String crearCuenta(SolicitudAlta solicitud);
}