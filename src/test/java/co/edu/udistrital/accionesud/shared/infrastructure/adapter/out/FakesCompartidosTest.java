package co.edu.udistrital.accionesud.shared.infrastructure.adapter.out;

import co.edu.udistrital.accionesud.shared.domain.broker.OrdenBroker;
import co.edu.udistrital.accionesud.shared.domain.exception.AccesoDenegadoException;
import co.edu.udistrital.accionesud.shared.domain.exception.BrokerOrdenesNoDisponibleException;
import co.edu.udistrital.accionesud.shared.domain.exception.FuncionPremiumRequeridaException;
import co.edu.udistrital.accionesud.shared.domain.premium.PlanSuscripcion;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.broker.BrokerOrdenesSimuladoAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.comisionistas.AccesoComisionistaEnMemoriaAdapter;
import co.edu.udistrital.accionesud.shared.infrastructure.adapter.out.premium.AccesoPremiumEnMemoriaAdapter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Adaptadores base / fakes de SCRUM-33 que usarán SCRUM-43, 52, 54 y 55. */
class FakesCompartidosTest {

    @Nested
    class Broker {

        private final BrokerOrdenesSimuladoAdapter broker = new BrokerOrdenesSimuladoAdapter();

        private OrdenBroker.Solicitud solicitud(String id) {
            return new OrdenBroker.Solicitud(id, "AAPL", OrdenBroker.Lado.COMPRA, 2, OrdenBroker.Tipo.MARKET, null, null);
        }

        @Test
        void reenviarElMismoIdClienteNoDuplicaLaOrden() {
            OrdenBroker.Respuesta primera = broker.enviar(solicitud("orden-1"));
            OrdenBroker.Respuesta reintento = broker.enviar(solicitud("orden-1"));

            assertThat(reintento.idExterno()).isEqualTo(primera.idExterno());
            assertThat(broker.envios()).isEqualTo(2);
            assertThat(broker.ordenesRegistradas()).isEqualTo(1);
            assertThat(primera.estado()).isEqualTo(OrdenBroker.Estado.ACEPTADA);
        }

        @Test
        void simulaCaidasYEstadosParaProbarLaSincronizacion() {
            broker.enviar(solicitud("orden-2"));
            broker.fijarEstado("orden-2", OrdenBroker.Estado.EJECUTADA, 2, new BigDecimal("190.10"));

            assertThat(broker.consultar("orden-2")).get()
                    .extracting(OrdenBroker.Respuesta::estado).isEqualTo(OrdenBroker.Estado.EJECUTADA);
            assertThat(broker.cancelar("orden-2").estado())
                    .as("una orden ejecutada no se cancela").isEqualTo(OrdenBroker.Estado.EJECUTADA);

            broker.simularCaida(true);
            assertThatThrownBy(() -> broker.enviar(solicitud("orden-3")))
                    .isInstanceOf(BrokerOrdenesNoDisponibleException.class);
        }

        @Test
        void cancelaOrdenesAbiertas() {
            broker.enviar(solicitud("orden-4"));
            assertThat(broker.cancelar("orden-4").estado()).isEqualTo(OrdenBroker.Estado.CANCELADA);
        }

        @Test
        void validaLaSolicitud() {
            assertThatThrownBy(() -> new OrdenBroker.Solicitud(" ", "AAPL", OrdenBroker.Lado.COMPRA, 1,
                    OrdenBroker.Tipo.MARKET, null, null)).hasMessageContaining("idCliente");
            assertThatThrownBy(() -> new OrdenBroker.Solicitud("x", "AAPL", OrdenBroker.Lado.COMPRA, 1,
                    OrdenBroker.Tipo.LIMIT, null, null)).hasMessageContaining("LIMIT");
            assertThatThrownBy(() -> new OrdenBroker.Solicitud("x", "AAPL", OrdenBroker.Lado.VENTA, 0,
                    OrdenBroker.Tipo.MARKET, null, null)).hasMessageContaining("cantidad");
        }
    }

    @Nested
    class Premium {

        @Test
        void soloLosUsuariosConfiguradosSonPremium() {
            UUID premium = UUID.randomUUID();
            UUID basico = UUID.randomUUID();
            AccesoPremiumEnMemoriaAdapter acceso = new AccesoPremiumEnMemoriaAdapter(List.of(premium));

            assertThat(acceso.planDe(premium)).isEqualTo(PlanSuscripcion.PREMIUM);
            assertThat(acceso.planDe(basico)).isEqualTo(PlanSuscripcion.BASICO);
            acceso.exigirPremium(premium);
            assertThatThrownBy(() -> acceso.exigirPremium(basico))
                    .isInstanceOf(FuncionPremiumRequeridaException.class)
                    .extracting("codigo").isEqualTo("FUNCION_PREMIUM");

            acceso.revocar(premium);
            assertThat(acceso.esPremium(premium)).isFalse();
        }
    }

    @Nested
    class Comisionistas {

        @Test
        void soloLaRelacionVigentePermiteAcceder() {
            AccesoComisionistaEnMemoriaAdapter acceso = new AccesoComisionistaEnMemoriaAdapter();
            UUID inversionista = UUID.randomUUID();
            UUID comisionista = UUID.randomUUID();
            UUID otro = UUID.randomUUID();

            assertThat(acceso.puedeAcceder(comisionista, inversionista)).as("por defecto se deniega").isFalse();

            acceso.asociar(inversionista, comisionista);
            acceso.exigirAcceso(comisionista, inversionista);
            assertThatThrownBy(() -> acceso.exigirAcceso(otro, inversionista))
                    .isInstanceOf(AccesoDenegadoException.class)
                    .extracting("codigo").isEqualTo("ACCESO_DENEGADO");

            acceso.asociar(inversionista, otro);
            assertThat(acceso.puedeAcceder(comisionista, inversionista)).as("tras el cambio").isFalse();
            assertThat(acceso.comisionistaVigenteDe(inversionista)).contains(otro);

            acceso.finalizar(inversionista);
            assertThat(acceso.comisionistaVigenteDe(inversionista)).isEmpty();
        }
    }
}
