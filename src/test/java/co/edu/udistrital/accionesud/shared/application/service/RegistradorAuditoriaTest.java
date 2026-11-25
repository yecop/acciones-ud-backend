package co.edu.udistrital.accionesud.shared.application.service;

import co.edu.udistrital.accionesud.shared.application.port.out.TransaccionPort;
import co.edu.udistrital.accionesud.shared.domain.auditoria.RegistroAuditoria;
import co.edu.udistrital.accionesud.shared.domain.auditoria.ResultadoAuditoria;
import co.edu.udistrital.accionesud.shared.domain.evento.MetadatosEvento;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.auditoria.AuditoriaEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.transaccion.TransaccionLocalAdapter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistradorAuditoriaTest {

    private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");
    private static final String TRACE = "11111111-2222-4333-8444-555555555555";

    private final AuditoriaEnMemoriaAdapter auditoria = new AuditoriaEnMemoriaAdapter();
    private final RegistradorAuditoria registrador =
            new RegistradorAuditoria(auditoria, () -> TRACE, Clock.fixed(AHORA, ZoneOffset.UTC));
    private final TransaccionPort transaccion = new TransaccionLocalAdapter();

    @Test
    void guardaActorFechaEntidadResultadoYTraceId() {
        UUID actor = UUID.randomUUID();
        registrador.exitoso(actor, "ORDEN_CREADA", "ORDEN", "o-1", Map.of("ticker", "AAPL"));

        RegistroAuditoria r = auditoria.registros().get(0);
        assertThat(r.actorId()).isEqualTo(actor);
        assertThat(r.ocurridoEn()).isEqualTo(AHORA);
        assertThat(r.accion()).isEqualTo("ORDEN_CREADA");
        assertThat(r.entidad()).isEqualTo("ORDEN");
        assertThat(r.entidadId()).isEqualTo("o-1");
        assertThat(r.resultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
        assertThat(r.detalle()).containsEntry("ticker", "AAPL");
        assertThat(r.traceId()).isEqualTo(TRACE);
    }

    @Test
    void losMetadatosDeEventoLlevanElMismoTraceId() {
        MetadatosEvento m = registrador.metadatosEvento(null);
        assertThat(m.traceId()).isEqualTo(TRACE);
        assertThat(m.ocurridoEn()).isEqualTo(AHORA);
        assertThat(m.id()).isNotNull();
    }

    @Test
    void laAuditoriaSeDeshaceConLaTransaccion() {
        assertThatThrownBy(() -> transaccion.enTransaccion(() -> {
            registrador.exitoso(null, "RETIRO", "CUENTA", "c-1", Map.of());
            throw new IllegalStateException("saldo insuficiente");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(auditoria.registros()).isEmpty();

        transaccion.enTransaccion(() -> registrador.exitoso(null, "DEPOSITO", "CUENTA", "c-1", Map.of()));
        assertThat(auditoria.registros()).extracting(RegistroAuditoria::accion).containsExactly("DEPOSITO");
    }

    @Test
    void validaCamposObligatoriosYLongitudes() {
        assertThatThrownBy(() -> new RegistroAuditoria(AHORA, null, "X", "Y", null, ResultadoAuditoria.EXITOSO,
                Map.of(), " ")).hasMessageContaining("traceId");
        assertThatThrownBy(() -> new RegistroAuditoria(AHORA, null, "", "Y", null, ResultadoAuditoria.EXITOSO,
                Map.of(), TRACE)).hasMessageContaining("accion");
        assertThatThrownBy(() -> new RegistroAuditoria(AHORA, null, "A".repeat(61), "Y", null,
                ResultadoAuditoria.EXITOSO, Map.of(), TRACE)).hasMessageContaining("60");
    }
}
