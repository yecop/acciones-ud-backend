package co.edu.udistrital.accionesud.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Entidad de dominio que agrega las posiciones de un usuario y permite calcular
 * el valor total del portafolio a partir de un proveedor de precios actuales.
 *
 * <p>Un portafolio sin posiciones es valido y representa el estado de un usuario
 * nuevo (portafolio vacio).
 */
public final class Portafolio {

    private final String usuarioId;
    private final List<Posicion> posiciones;

    public Portafolio(String usuarioId, List<Posicion> posiciones) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "El usuarioId no puede ser nulo");
        this.posiciones = posiciones == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(posiciones));
    }

    /** Crea un portafolio vacio para un usuario nuevo. */
    public static Portafolio vacio(String usuarioId) {
        return new Portafolio(usuarioId, Collections.emptyList());
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    /** Lista inmutable de posiciones. */
    public List<Posicion> getPosiciones() {
        return posiciones;
    }

    public boolean estaVacio() {
        return posiciones.isEmpty();
    }

    /**
     * Valor total de mercado del portafolio: suma del valor actual de cada
     * posicion. El precio actual de cada activo lo entrega el proveedor.
     *
     * @param precioActualPorActivo funcion que, dado un ticker, devuelve su
     *                              precio de mercado vigente (desde la cache).
     * @return valor total con 2 decimales.
     */
    public BigDecimal valorTotal(Function<String, BigDecimal> precioActualPorActivo) {
        Objects.requireNonNull(precioActualPorActivo, "El proveedor de precios no puede ser nulo");
        BigDecimal total = BigDecimal.ZERO;
        for (Posicion posicion : posiciones) {
            BigDecimal precio = precioActualPorActivo.apply(posicion.getActivo());
            total = total.add(posicion.valorActual(precio));
        }
        return total.setScale(Posicion.ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Costo base total del portafolio: suma del costo base de cada posicion.
     *
     * @return costo total con 2 decimales.
     */
    public BigDecimal costoTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (Posicion posicion : posiciones) {
            total = total.add(posicion.costoBase());
        }
        return total.setScale(Posicion.ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Ganancia o perdida total del portafolio = valorTotal - costoTotal.
     *
     * @param precioActualPorActivo proveedor de precios de mercado.
     * @return P&amp;L total con 2 decimales.
     */
    public BigDecimal gananciaPerdidaTotal(Function<String, BigDecimal> precioActualPorActivo) {
        return valorTotal(precioActualPorActivo).subtract(costoTotal())
                .setScale(Posicion.ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
