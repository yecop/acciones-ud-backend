package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion;

import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link TransaccionPort} sobre el gestor de transacciones de Spring (JDBC/PostgreSQL).
 * Propagación REQUIRED: una llamada anidada se une a la transacción exterior. Una excepción no
 * verificada hace rollback.
 */
public class TransaccionSpringAdapter implements TransaccionPort {

    private final TransactionTemplate plantilla;

    public TransaccionSpringAdapter(TransactionTemplate plantilla) {
        this.plantilla = Objects.requireNonNull(plantilla);
    }

    @Override
    public <T> T enTransaccion(Supplier<T> trabajo) {
        return plantilla.execute(estado -> trabajo.get());
    }
}
