package co.edu.udistrital.accionesud.mercado.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketTest {

    private static final Instant T0 = Instant.parse("2026-01-05T15:00:00Z");

    @Test
    void permiteHastaLaCapacidadYLuegoRechaza() {
        TokenBucket balde = new TokenBucket(3, 3, Duration.ofMinutes(1), T0);

        assertThat(balde.intentarConsumir(T0)).isTrue();
        assertThat(balde.intentarConsumir(T0)).isTrue();
        assertThat(balde.intentarConsumir(T0)).isTrue();
        assertThat(balde.intentarConsumir(T0)).isFalse();
    }

    @Test
    void recargaDeFormaContinuaSegunElTiempoTranscurrido() {
        // 120 fichas por minuto = 1 ficha cada 0,5 s
        TokenBucket balde = new TokenBucket(120, 120, Duration.ofMinutes(1), T0);
        for (int i = 0; i < 120; i++) {
            assertThat(balde.intentarConsumir(T0)).isTrue();
        }
        assertThat(balde.intentarConsumir(T0)).isFalse();

        assertThat(balde.intentarConsumir(T0.plusMillis(499))).isFalse();
        assertThat(balde.intentarConsumir(T0.plusMillis(500))).isTrue();
        assertThat(balde.fichasDisponibles(T0.plusSeconds(5).plusMillis(500))).isEqualTo(10);
    }

    @Test
    void nuncaSuperaLaCapacidad() {
        TokenBucket balde = new TokenBucket(5, 5, Duration.ofSeconds(1), T0);
        assertThat(balde.fichasDisponibles(T0.plusSeconds(3600))).isEqualTo(5);
    }

    @Test
    void validaParametros() {
        assertThatThrownBy(() -> new TokenBucket(0, 1, Duration.ofSeconds(1), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, 0, Duration.ofSeconds(1), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, 1, Duration.ZERO, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
