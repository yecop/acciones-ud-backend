package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion;

import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@link TransaccionPort} para entornos sin base de datos (perfil {@code test} sin
 * PostgreSQL, almacenamiento en memoria).
 *
 * <p>No hay recursos que confirmar, pero respeta el mismo contrato que la transacción real:
 * las sincronizaciones registradas (p. ej. eventos) solo reciben {@code afterCommit} si el
 * trabajo termina sin excepción; si falla, reciben {@code afterCompletion(ROLLED_BACK)} y los
 * eventos se descartan. Las llamadas anidadas se unen a la exterior.</p>
 */
public class TransaccionLocalAdapter implements TransaccionPort {

    private static final Logger log = LoggerFactory.getLogger(TransaccionLocalAdapter.class);

    @Override
    public <T> T enTransaccion(Supplier<T> trabajo) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            return trabajo.get();
        }
        TransactionSynchronizationManager.initSynchronization();
        boolean exito = false;
        try {
            T resultado = trabajo.get();
            exito = true;
            return resultado;
        } finally {
            List<TransactionSynchronization> sincronizaciones = TransactionSynchronizationManager.getSynchronizations();
            // Se limpia antes de notificar: lo que se publique en afterCommit sale de inmediato.
            TransactionSynchronizationManager.clearSynchronization();
            if (exito) {
                sincronizaciones.forEach(s -> ejecutar(s::afterCommit));
                sincronizaciones.forEach(s -> ejecutar(() -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)));
            } else {
                sincronizaciones.forEach(s -> ejecutar(() -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)));
            }
        }
    }

    private static void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            log.error("Error en una sincronización posterior a la transacción", e);
        }
    }
}
