package co.edu.udistrital.accionesud.mercado.support;

import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Reloj de pruebas que solo avanza cuando la prueba lo indica (seguro para hilos). */
public class RelojControlable implements RelojPort {

    private final AtomicReference<Instant> ahora;

    public RelojControlable(Instant inicio) {
        this.ahora = new AtomicReference<>(inicio);
    }

    @Override
    public Instant ahora() {
        return ahora.get();
    }

    public void fijar(Instant instante) {
        ahora.set(instante);
    }

    public void avanzar(Duration duracion) {
        ahora.updateAndGet(i -> i.plus(duracion));
    }
}
