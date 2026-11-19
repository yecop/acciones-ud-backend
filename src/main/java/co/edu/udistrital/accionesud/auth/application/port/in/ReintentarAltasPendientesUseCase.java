package co.edu.udistrital.accionesud.auth.application.port.in;

public interface ReintentarAltasPendientesUseCase {

    /** Reintenta el alta de hasta {@code maximo} usuarios pendientes. Devuelve cuántas se completaron. */
    int reintentar(int maximo);
}