package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.reloj;

import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Adaptador de salida que implementa {@link RelojPort} usando el reloj real del sistema.
 *
 * <p>Se apoya en {@link Clock} para que, de ser necesario, pueda sustituirse por un
 * reloj fijo en pruebas de integración sin cambiar la lógica.</p>
 */
@Component
public class RelojSistemaAdapter implements RelojPort {

    private final Clock clock;

    public RelojSistemaAdapter() {
        this(Clock.systemUTC());
    }

    public RelojSistemaAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant ahora() {
        return clock.instant();
    }
}
