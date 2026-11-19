package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto;

import java.util.List;

/**
 * Página del contrato: {@code contenido, pagina, tamano, totalElementos, totalPaginas}.
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
}
