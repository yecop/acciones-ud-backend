package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.util.MoneyFormatter;
import java.time.Instant;
import java.util.UUID;

/** Un movimiento de fondos; el monto viaja como texto con 2 decimales, como el resto del contrato. */
public record MovimientoFondosResponse(UUID id,
                                       String tipo,
                                       String estado,
                                       String monto,
                                       String moneda,
                                       Instant creadoEn,
                                       Instant completadoEn,
                                       String traceId) {

    public static MovimientoFondosResponse desde(MovimientoFondos m) {
        return new MovimientoFondosResponse(m.id(), m.tipo().name(), m.estado().name(),
                MoneyFormatter.format(m.monto()), m.moneda(), m.creadoEn(), m.completadoEn(), m.traceId());
    }
}
