package co.edu.udistrital.accionesud.cuenta.application.dto;

import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import java.util.List;

/** Página de movimientos de fondos, con la misma forma que el resto de listas del contrato. */
public record PaginaMovimientos(List<MovimientoFondos> contenido, int pagina, int tamano,
                                long totalElementos, int totalPaginas) {

    public PaginaMovimientos {
        contenido = List.copyOf(contenido);
    }

    public static PaginaMovimientos de(List<MovimientoFondos> contenido, int pagina, int tamano, long total) {
        int paginas = (int) ((total + tamano - 1) / tamano);
        return new PaginaMovimientos(contenido, pagina, tamano, total, paginas);
    }
}
