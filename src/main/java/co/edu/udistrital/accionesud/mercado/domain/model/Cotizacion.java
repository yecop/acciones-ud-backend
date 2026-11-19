package co.edu.udistrital.accionesud.mercado.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Cotización de una acción tal como se guarda en la caché por ticker (SCRUM-24).
 *
 * <p>Contiene precio, bid, ask, spread, volumen, la fecha/hora en que el backend
 * actualizó el dato ({@code actualizadoEn}) y la fuente que lo produjo. El spread se
 * deriva siempre de {@code ask - bid} para que nunca quede inconsistente.</p>
 *
 * @param ticker        símbolo en mayúsculas (p. ej. "AAPL").
 * @param precio        último precio negociado.
 * @param bid           mejor precio de compra (puede ser {@code null} si el proveedor no lo reporta).
 * @param ask           mejor precio de venta (puede ser {@code null} si el proveedor no lo reporta).
 * @param spread        {@code ask - bid}; {@code null} si falta alguno de los dos.
 * @param volumen       volumen negociado en la sesión.
 * @param actualizadoEn instante en que el backend obtuvo el dato del proveedor.
 * @param fuente        identificador de la fuente (p. ej. "ALPACA_IEX").
 */
public record Cotizacion(
        String ticker,
        BigDecimal precio,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal spread,
        long volumen,
        Instant actualizadoEn,
        String fuente) {

    public Cotizacion {
        Objects.requireNonNull(ticker, "El ticker es obligatorio");
        Objects.requireNonNull(precio, "El precio es obligatorio");
        Objects.requireNonNull(actualizadoEn, "La fecha de actualización es obligatoria");
        Objects.requireNonNull(fuente, "La fuente es obligatoria");
        ticker = ticker.trim().toUpperCase(Locale.ROOT);
        if (volumen < 0) {
            throw new IllegalArgumentException("El volumen no puede ser negativo");
        }
    }

    /** Crea una cotización calculando el spread a partir del bid y el ask. */
    public static Cotizacion de(String ticker, BigDecimal precio, BigDecimal bid, BigDecimal ask,
                                long volumen, Instant actualizadoEn, String fuente) {
        BigDecimal spread = (bid != null && ask != null) ? ask.subtract(bid) : null;
        return new Cotizacion(ticker, precio, bid, ask, spread, volumen, actualizadoEn, fuente);
    }
}
