package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.auditoria;

import co.edu.udistrital.accionesud.shared.application.port.out.RegistroAuditoriaPort;
import co.edu.udistrital.accionesud.shared.domain.auditoria.RegistroAuditoria;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Auditoría en memoria para pruebas y entornos sin base de datos (perfil {@code test}).
 *
 * <p>Imita la base de datos dentro de una transacción: el registro solo queda guardado si la
 * transacción confirma; con rollback se descarta. Fuera de transacción se guarda al instante.</p>
 */
public class AuditoriaEnMemoriaAdapter implements RegistroAuditoriaPort {

    private final List<RegistroAuditoria> registros = new CopyOnWriteArrayList<>();

    @Override
    public void registrar(RegistroAuditoria registro) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    registros.add(registro);
                }
            });
        } else {
            registros.add(registro);
        }
    }

    /** Copia inmutable de lo registrado (solo lectura: la auditoría no se edita). */
    public List<RegistroAuditoria> registros() {
        return List.copyOf(registros);
    }

    /** Solo para aislar pruebas. */
    public void limpiar() {
        registros.clear();
    }
}
