package co.edu.udistrital.accionesud.mercado.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calendario de feriados configurable por mercado.
 *
 * <p>Vive en el dominio pero su contenido es inyectado desde la configuración del
 * sistema (lista configurable), cumpliendo así con el requisito de feriados
 * administrables sin tocar la lógica de negocio.</p>
 */
public final class CalendarioFeriados {

    private final Map<CodigoMercado, Map<LocalDate, Restriccion>> feriadosPorMercado;

    private CalendarioFeriados(Map<CodigoMercado, Map<LocalDate, Restriccion>> feriadosPorMercado) {
        this.feriadosPorMercado = feriadosPorMercado;
    }

    public static CalendarioFeriados vacio() {
        return new CalendarioFeriados(new EnumMap<>(CodigoMercado.class));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean esFeriado(CodigoMercado codigo, LocalDate fecha) {
        return feriadosPorMercado.getOrDefault(codigo, Map.of()).containsKey(fecha);
    }

    public List<Restriccion> restriccionesPara(CodigoMercado codigo, LocalDate fecha) {
        Restriccion restriccion = feriadosPorMercado.getOrDefault(codigo, Map.of()).get(fecha);
        if (restriccion == null) {
            return List.of();
        }
        List<Restriccion> resultado = new ArrayList<>();
        resultado.add(restriccion);
        return Collections.unmodifiableList(resultado);
    }

    public static final class Builder {
        private final Map<CodigoMercado, Map<LocalDate, Restriccion>> datos =
                new EnumMap<>(CodigoMercado.class);

        public Builder agregarFeriado(CodigoMercado codigo, LocalDate fecha, String descripcion) {
            Objects.requireNonNull(codigo, "El código es obligatorio");
            Objects.requireNonNull(fecha, "La fecha es obligatoria");
            datos.computeIfAbsent(codigo, c -> new HashMap<>())
                    .put(fecha, Restriccion.de("FERIADO", descripcion));
            return this;
        }

        public CalendarioFeriados construir() {
            Map<CodigoMercado, Map<LocalDate, Restriccion>> copia = new EnumMap<>(CodigoMercado.class);
            datos.forEach((codigo, mapa) -> copia.put(codigo, Map.copyOf(mapa)));
            return new CalendarioFeriados(copia);
        }
    }
}
