package co.edu.udistrital.accionesud.shared.application.port.out;

import java.util.function.Supplier;

/**
 * Unidad de trabajo transaccional para los servicios de aplicación (SCRUM-33).
 *
 * <p>Permite que un caso de uso escriba en varias tablas, registre auditoría y publique
 * eventos de forma atómica sin depender de Spring. Si el trabajo lanza una excepción, todo se
 * deshace y los eventos publicados dentro no se entregan. Llamadas anidadas se unen a la
 * transacción exterior.</p>
 */
public interface TransaccionPort {

    <T> T enTransaccion(Supplier<T> trabajo);

    default void enTransaccion(Runnable trabajo) {
        enTransaccion(() -> {
            trabajo.run();
            return null;
        });
    }
}
