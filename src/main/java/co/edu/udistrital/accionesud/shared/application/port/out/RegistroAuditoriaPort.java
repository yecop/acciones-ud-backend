package co.edu.udistrital.accionesud.shared.application.port.out;

import co.edu.udistrital.accionesud.shared.domain.auditoria.RegistroAuditoria;

/**
 * Persiste eventos auditables (SCRUM-33). Solo permite agregar: no hay operaciones para
 * editar ni borrar (SCRUM-42). Dentro de una {@link TransaccionPort} el registro forma parte
 * de la misma transacción que el cambio de negocio.
 */
public interface RegistroAuditoriaPort {

    void registrar(RegistroAuditoria registro);
}
