package co.edu.udistrital.accionesud.ordenes.application.port.out;

import java.util.UUID;

/** Consulta la cantidad de acciones que el inversionista aún puede vender. */
public interface PosicionDisponiblePort {

    /** Cantidad total menos la ya reservada para otras órdenes. */
    long cantidadDisponible(UUID inversionistaId, String ticker);
}
