package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.evento;

import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;
import co.edu.udistrital.accionesud.shared.domain.evento.MetadatosEvento;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionLocalAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Criterios de SCRUM-33: los eventos solo se publican después del commit y un rollback no
 * produce eventos externos. Se usa la transacción local (sin base de datos).
 */
class PublicadorEventosTrasCommitTest {

    private final List<Object> entregados = new CopyOnWriteArrayList<>();
    private final PublicadorEventosTrasCommitAdapter publicador = new PublicadorEventosTrasCommitAdapter(entregados::add);
    private final TransaccionPort transaccion = new TransaccionLocalAdapter();

    private static EventoDominio evento(String nombre) {
        return new Eventos.MovimientoFondosRegistrado(MetadatosEvento.nuevo(Instant.now(), "trace-" + nombre, null),
                UUID.randomUUID(), nombre, BigDecimal.TEN, BigDecimal.TEN);
    }

    @AfterEach
    void sinSincronizacionesColgadas() {
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    @Test
    void dentroDeLaTransaccionNoSeEntregaHastaElCommit() {
        EventoDominio deposito = evento("DEPOSITO");

        transaccion.enTransaccion(() -> {
            publicador.publicar(deposito);
            assertThat(entregados).as("antes del commit").isEmpty();
        });

        assertThat(entregados).containsExactly(deposito);
    }

    @Test
    void unRollbackNoProduceEventos() {
        assertThatThrownBy(() -> transaccion.enTransaccion(() -> {
            publicador.publicar(evento("DEPOSITO"));
            throw new IllegalStateException("falla después de publicar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(entregados).isEmpty();
    }

    @Test
    void sinTransaccionSeEntregaDeInmediato() {
        EventoDominio retiro = evento("RETIRO");
        publicador.publicar(retiro);
        assertThat(entregados).containsExactly(retiro);
    }

    @Test
    void lasTransaccionesAnidadasEsperanAlCommitExteriorYConservanElOrden() {
        EventoDominio primero = evento("1");
        EventoDominio segundo = evento("2");

        transaccion.enTransaccion(() -> {
            publicador.publicar(primero);
            transaccion.enTransaccion(() -> publicador.publicar(segundo));
            assertThat(entregados).as("la interna no confirma por su cuenta").isEmpty();
        });

        assertThat(entregados).containsExactly(primero, segundo);
    }

    @Test
    void siFallaLaInternaSeDeshaceTodo() {
        assertThatThrownBy(() -> transaccion.enTransaccion(() -> {
            publicador.publicar(evento("1"));
            transaccion.enTransaccion(() -> {
                publicador.publicar(evento("2"));
                throw new IllegalArgumentException("regla de negocio");
            });
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(entregados).isEmpty();
    }

    @Test
    void unConsumidorQueFallaNoAfectaAlLlamadorNiAlResto() {
        List<Object> recibidos = new CopyOnWriteArrayList<>();
        PublicadorEventosTrasCommitAdapter conFallo = new PublicadorEventosTrasCommitAdapter(e -> {
            recibidos.add(e);
            if (recibidos.size() == 1) {
                throw new IllegalStateException("consumidor roto");
            }
        });

        String resultado = transaccion.enTransaccion(() -> {
            conFallo.publicar(evento("1"));
            conFallo.publicar(evento("2"));
            return "confirmado";
        });

        assertThat(resultado).isEqualTo("confirmado");
        assertThat(recibidos).hasSize(2);
    }
}
