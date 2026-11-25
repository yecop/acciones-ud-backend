package co.edu.udistrital.accionesud.shared.domain.auditoria;

/** Resultado de una acción auditada. */
public enum ResultadoAuditoria {
    /** La acción se completó. */
    EXITOSO,
    /** Se rechazó por reglas de negocio o permisos. */
    RECHAZADO,
    /** Falló por un error técnico o de un proveedor. */
    FALLIDO
}
