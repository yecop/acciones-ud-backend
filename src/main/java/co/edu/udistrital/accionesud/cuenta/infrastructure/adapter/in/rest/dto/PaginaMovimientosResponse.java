package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto;

import co.edu.udistrital.accionesud.cuenta.application.dto.PaginaMovimientos;
import java.util.List;

public record PaginaMovimientosResponse(List<MovimientoFondosResponse> contenido, int pagina, int tamano,
                                        long totalElementos, int totalPaginas) {

    public static PaginaMovimientosResponse desde(PaginaMovimientos p) {
        return new PaginaMovimientosResponse(p.contenido().stream().map(MovimientoFondosResponse::desde).toList(),
                p.pagina(), p.tamano(), p.totalElementos(), p.totalPaginas());
    }
}
