package co.edu.udistrital.accionesud.mercado.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Regla de negocio de antigüedad de cotizaciones (SCRUM-24).
 *
 * <ul>
 *     <li>Un dato de EE. UU. con más de {@code umbralEstadosUnidos} (15 s) se marca como
 *     desactualizado y lleva advertencia {@code DATO_DESACTUALIZADO}.</li>
 *     <li>Los mercados internacionales (LSE, TSE, ASX) usan {@code umbralInternacional}, más
 *     amplio, porque los planes gratuitos de sus proveedores permiten pocas llamadas.</li>
 *     <li>Si el proveedor del mercado está fallando, el último dato se sirve igual pero con
 *     la advertencia {@code PROVEEDOR_NO_DISPONIBLE} (tiene prioridad sobre la anterior).</li>
 * </ul>
 */
public final class PoliticaAntiguedad {

    private final Duration umbralEstadosUnidos;
    private final Duration umbralInternacional;

    public PoliticaAntiguedad(Duration umbralEstadosUnidos, Duration umbralInternacional) {
        this.umbralEstadosUnidos = validar(umbralEstadosUnidos);
        this.umbralInternacional = validar(umbralInternacional);
    }

    /** Mismo umbral para todos los mercados. */
    public PoliticaAntiguedad(Duration umbral) {
        this(umbral, umbral);
    }

    private static Duration validar(Duration umbral) {
        Objects.requireNonNull(umbral, "El umbral es obligatorio");
        if (umbral.isNegative() || umbral.isZero()) {
            throw new IllegalArgumentException("El umbral de antigüedad debe ser positivo");
        }
        return umbral;
    }

    /** Umbral aplicable a la acción según su mercado. */
    public Duration umbralPara(Accion accion) {
        return accion.esDeEstadosUnidos() ? umbralEstadosUnidos : umbralInternacional;
    }

    /**
     * Evalúa una cotización leída de la caché.
     *
     * @param accion              acción a la que pertenece la cotización.
     * @param cotizacion          dato leído de la caché.
     * @param ahora               instante de la consulta.
     * @param proveedorDisponible {@code false} si la última consulta a la fuente del mercado falló.
     */
    public CotizacionVigente evaluar(Accion accion, Cotizacion cotizacion, Instant ahora,
                                     boolean proveedorDisponible) {
        Objects.requireNonNull(accion, "La acción es obligatoria");
        Objects.requireNonNull(cotizacion, "La cotización es obligatoria");
        Objects.requireNonNull(ahora, "El instante es obligatorio");

        Duration umbral = umbralPara(accion);
        Duration antiguedad = Duration.between(cotizacion.actualizadoEn(), ahora);
        if (antiguedad.isNegative()) {
            antiguedad = Duration.ZERO;
        }
        long segundos = antiguedad.toSeconds();
        boolean desactualizada = antiguedad.compareTo(umbral) > 0;

        Advertencia advertencia = null;
        if (!proveedorDisponible) {
            // El dato ya no se está refrescando: se marca como no vigente aunque sea reciente.
            desactualizada = true;
            advertencia = new Advertencia(Advertencia.PROVEEDOR_NO_DISPONIBLE,
                    "El proveedor de datos no responde; se muestra el último dato disponible ("
                            + segundos + " s de antigüedad).");
        } else if (desactualizada) {
            advertencia = new Advertencia(Advertencia.DATO_DESACTUALIZADO,
                    "La cotización tiene " + segundos + " s de antigüedad (máximo esperado: "
                            + umbral.toSeconds() + " s).");
        }
        return new CotizacionVigente(cotizacion, segundos, umbral.toSeconds(), desactualizada, advertencia);
    }
}
