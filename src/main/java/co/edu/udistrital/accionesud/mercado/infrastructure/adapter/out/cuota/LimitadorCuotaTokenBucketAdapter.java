package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.cuota;

import co.edu.udistrital.accionesud.mercado.domain.model.EstadisticasCuota;
import co.edu.udistrital.accionesud.mercado.domain.model.TokenBucket;
import co.edu.udistrital.accionesud.mercado.domain.port.out.LimitadorCuotaPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Limitador de cuota de un proveedor de datos basado en Token Bucket (SCRUM-24).
 *
 * <p>Se crea una instancia por proveedor (Alpaca, Alpha Vantage, Finnhub, Polygon). Cada
 * proveedor declara uno o varios límites de su plan (p. ej. 200/min en Alpaca; 5/min y
 * 25/día en Alpha Vantage). El tope de diseño de cada límite es {@code porcentajeTope}%
 * (60 % por defecto).</p>
 *
 * <ul>
 *   <li><b>Token Bucket</b> por límite: capacidad = tope, recarga = tope por periodo.</li>
 *   <li><b>Ventana móvil</b> por límite: cuenta las llamadas reales y garantiza que en
 *   ningún periodo se supere el tope (el balde por sí solo permitiría hasta 2×).</li>
 *   <li><b>Alarma</b>: si un intento superaría algún tope, se rechaza y se registra un log
 *   {@code ERROR} con el marcador {@code ALARMA_CUOTA_<PROVEEDOR>}.</li>
 * </ul>
 */
public class LimitadorCuotaTokenBucketAdapter implements LimitadorCuotaPort {

    public static final String PREFIJO_ALARMA = "ALARMA_CUOTA_";

    private static final Logger log = LoggerFactory.getLogger(LimitadorCuotaTokenBucketAdapter.class);
    /** Evita inundar el log: como máximo una alarma cada 10 s. */
    private static final Duration ESPACIO_ENTRE_ALARMAS = Duration.ofSeconds(10);

    /** Límite del plan de un proveedor: {@code llamadas} por {@code periodo}. */
    public record Limite(long llamadas, Duration periodo) {
        public Limite {
            if (llamadas <= 0 || periodo == null || periodo.isZero() || periodo.isNegative()) {
                throw new IllegalArgumentException("Límite de cuota inválido: " + llamadas + "/" + periodo);
            }
        }

        public static Limite porMinuto(long llamadas) {
            return new Limite(llamadas, Duration.ofMinutes(1));
        }

        public static Limite porDia(long llamadas) {
            return new Limite(llamadas, Duration.ofDays(1));
        }
    }

    private final String proveedor;
    private final String marcadorAlarma;
    private final RelojPort reloj;
    private final List<Ventana> ventanas = new ArrayList<>();

    private long llamadasTotales;
    private long llamadasRechazadas;
    private long alarmasEmitidas;
    private Instant ultimaAlarma;

    public LimitadorCuotaTokenBucketAdapter(String proveedor, List<Limite> limites, int porcentajeTope,
                                            RelojPort reloj) {
        if (porcentajeTope <= 0 || porcentajeTope > 100) {
            throw new IllegalArgumentException("El porcentaje del tope debe estar entre 1 y 100");
        }
        if (limites == null || limites.isEmpty()) {
            throw new IllegalArgumentException("Debe existir al menos un límite de cuota");
        }
        this.proveedor = proveedor;
        this.marcadorAlarma = PREFIJO_ALARMA + proveedor.toUpperCase(Locale.ROOT);
        this.reloj = reloj;
        Instant inicio = reloj.ahora();
        for (Limite limite : limites) {
            long tope = Math.max(1, (limite.llamadas() * porcentajeTope) / 100);
            ventanas.add(new Ventana(limite, tope, new TokenBucket(tope, tope, limite.periodo(), inicio)));
            log.info("Limitador de cuota {}: límite {} llamadas/{}, tope de diseño {}% = {}",
                    proveedor, limite.llamadas(), limite.periodo(), porcentajeTope, tope);
        }
    }

    @Override
    public synchronized boolean intentarConsumir() {
        Instant ahora = reloj.ahora();
        ventanas.forEach(v -> v.purgar(ahora));

        Ventana excedida = ventanas.stream().filter(v -> !v.tieneEspacio(ahora)).findFirst().orElse(null);
        if (excedida == null) {
            ventanas.forEach(v -> v.registrar(ahora));
            llamadasTotales++;
            return true;
        }
        llamadasRechazadas++;
        emitirAlarma(ahora, excedida);
        return false;
    }

    @Override
    public synchronized EstadisticasCuota estadisticas() {
        Instant ahora = reloj.ahora();
        ventanas.forEach(v -> v.purgar(ahora));
        Ventana principal = ventanas.get(0);
        return new EstadisticasCuota(proveedor, principal.limite.llamadas(), principal.tope,
                principal.llamadas.size(), llamadasTotales, llamadasRechazadas, alarmasEmitidas);
    }

    private void emitirAlarma(Instant ahora, Ventana ventana) {
        if (ultimaAlarma != null && Duration.between(ultimaAlarma, ahora).compareTo(ESPACIO_ENTRE_ALARMAS) < 0) {
            return;
        }
        ultimaAlarma = ahora;
        alarmasEmitidas++;
        log.error("{}: se intentó superar el tope de diseño ({} de {} llamadas por {}). "
                        + "Llamadas en la ventana: {}, rechazadas acumuladas: {}",
                marcadorAlarma, ventana.tope, ventana.limite.llamadas(), ventana.limite.periodo(),
                ventana.llamadas.size(), llamadasRechazadas);
    }

    /** Estado de un límite: balde de fichas + registro de llamadas en la ventana móvil. */
    private static final class Ventana {
        private final Limite limite;
        private final long tope;
        private final TokenBucket balde;
        private final Deque<Instant> llamadas = new ArrayDeque<>();

        private Ventana(Limite limite, long tope, TokenBucket balde) {
            this.limite = limite;
            this.tope = tope;
            this.balde = balde;
        }

        void purgar(Instant ahora) {
            Instant desde = ahora.minus(limite.periodo());
            while (!llamadas.isEmpty() && !llamadas.peekFirst().isAfter(desde)) {
                llamadas.removeFirst();
            }
        }

        boolean tieneEspacio(Instant ahora) {
            return llamadas.size() < tope && balde.fichasDisponibles(ahora) >= 1;
        }

        void registrar(Instant ahora) {
            balde.intentarConsumir(ahora);
            llamadas.addLast(ahora);
        }
    }
}
