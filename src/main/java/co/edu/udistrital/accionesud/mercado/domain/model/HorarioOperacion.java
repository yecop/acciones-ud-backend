package co.edu.udistrital.accionesud.mercado.domain.model;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Representa la ventana diaria de operación de un mercado expresada en su hora local.
 */
public final class HorarioOperacion {

    private final LocalTime apertura;
    private final LocalTime cierre;

    public HorarioOperacion(LocalTime apertura, LocalTime cierre) {
        this.apertura = Objects.requireNonNull(apertura, "La apertura es obligatoria");
        this.cierre = Objects.requireNonNull(cierre, "El cierre es obligatorio");
        if (!cierre.isAfter(apertura)) {
            throw new IllegalArgumentException("El cierre debe ser posterior a la apertura");
        }
    }

    public static HorarioOperacion de(LocalTime apertura, LocalTime cierre) {
        return new HorarioOperacion(apertura, cierre);
    }

    public LocalTime apertura() {
        return apertura;
    }

    public LocalTime cierre() {
        return cierre;
    }

    /**
     * Indica si la hora local proporcionada cae dentro de la ventana.
     * El límite inferior es inclusivo (apertura) y el superior exclusivo (cierre),
     * de modo que en el instante exacto de cierre el mercado se considera cerrado.
     */
    public boolean contiene(LocalTime horaLocal) {
        return !horaLocal.isBefore(apertura) && horaLocal.isBefore(cierre);
    }
}
