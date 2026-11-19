package co.edu.udistrital.accionesud.mercado.application.dto;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados (contrato: {@code contenido, pagina, tamano, totalElementos, totalPaginas}).
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    /** Recorta la lista completa a la página solicitada (base 0). */
    public static <T> Pagina<T> de(List<T> todos, int pagina, int tamano) {
        if (pagina < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        if (tamano <= 0) {
            throw new IllegalArgumentException("El tamaño de página debe ser positivo");
        }
        int desde = (int) Math.min((long) pagina * tamano, todos.size());
        int hasta = Math.min(desde + tamano, todos.size());
        int totalPaginas = (int) Math.ceil((double) todos.size() / tamano);
        return new Pagina<>(todos.subList(desde, hasta), pagina, tamano, todos.size(), totalPaginas);
    }

    public <R> Pagina<R> map(Function<T, R> funcion) {
        return new Pagina<>(contenido.stream().map(funcion).toList(), pagina, tamano, totalElementos, totalPaginas);
    }
}
