package co.edu.udistrital.accionesud.ordenes.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.ordenes.application.port.out.PosicionDisponiblePort;
import java.util.UUID;

/** Adaptador de respaldo para perfiles sin base de datos: no hay acciones disponibles. */
public class PosicionDisponibleEnMemoriaAdapter implements PosicionDisponiblePort {

    @Override
    public long cantidadDisponible(UUID inversionistaId, String ticker) {
        return 0;
    }
}
