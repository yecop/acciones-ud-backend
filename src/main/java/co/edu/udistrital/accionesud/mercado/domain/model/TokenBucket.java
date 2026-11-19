package co.edu.udistrital.accionesud.mercado.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Algoritmo Token Bucket puro (sin dependencias de framework ni del reloj real).
 *
 * <p>El balde arranca lleno con {@code capacidad} fichas y se recarga de forma
 * continua a razón de {@code fichasPorPeriodo} cada {@code periodo}. Cada llamada al
 * proveedor consume una ficha; si no hay fichas, la llamada se rechaza. El instante
 * se recibe por parámetro para que las pruebas sean deterministas.</p>
 *
 * <p>Es seguro para hilos: los métodos públicos están sincronizados.</p>
 */
public final class TokenBucket {

    private final long capacidad;
    private final double fichasPorNano;
    private double fichas;
    private Instant ultimaRecarga;

    public TokenBucket(long capacidad, long fichasPorPeriodo, Duration periodo, Instant inicio) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser positiva");
        }
        if (fichasPorPeriodo <= 0) {
            throw new IllegalArgumentException("La recarga debe ser positiva");
        }
        Objects.requireNonNull(periodo, "El periodo es obligatorio");
        if (periodo.isZero() || periodo.isNegative()) {
            throw new IllegalArgumentException("El periodo debe ser positivo");
        }
        this.capacidad = capacidad;
        this.fichasPorNano = (double) fichasPorPeriodo / periodo.toNanos();
        this.fichas = capacidad;
        this.ultimaRecarga = Objects.requireNonNull(inicio, "El instante inicial es obligatorio");
    }

    /**
     * Intenta consumir una ficha en el instante dado.
     *
     * @return {@code true} si había ficha disponible (la llamada está permitida).
     */
    public synchronized boolean intentarConsumir(Instant ahora) {
        recargar(ahora);
        if (fichas >= 1.0) {
            fichas -= 1.0;
            return true;
        }
        return false;
    }

    /** Fichas disponibles (enteras) en el instante dado. */
    public synchronized long fichasDisponibles(Instant ahora) {
        recargar(ahora);
        return (long) Math.floor(fichas);
    }

    public long capacidad() {
        return capacidad;
    }

    private void recargar(Instant ahora) {
        Objects.requireNonNull(ahora, "El instante es obligatorio");
        if (ahora.isAfter(ultimaRecarga)) {
            long nanos = Duration.between(ultimaRecarga, ahora).toNanos();
            fichas = Math.min(capacidad, fichas + nanos * fichasPorNano);
            ultimaRecarga = ahora;
        }
    }
}
