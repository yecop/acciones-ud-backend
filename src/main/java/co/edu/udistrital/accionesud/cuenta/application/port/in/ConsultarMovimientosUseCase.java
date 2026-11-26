package co.edu.udistrital.accionesud.cuenta.application.port.in;

import co.edu.udistrital.accionesud.cuenta.application.dto.PaginaMovimientos;
import co.edu.udistrital.accionesud.cuenta.domain.model.TipoMovimiento;
import java.util.UUID;

/** Historial de movimientos de fondos de la cuenta del inversionista (SCRUM-46). */
public interface ConsultarMovimientosUseCase {

    int TAMANO_MAXIMO = 100;

    /** @param tipo {@code null} para todos los tipos. */
    PaginaMovimientos listar(UUID inversionistaId, TipoMovimiento tipo, int pagina, int tamano);
}
