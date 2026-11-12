package co.edu.udistrital.accionesud.mercado.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura exhaustiva de los escenarios de estado para los cinco mercados del
 * catalogo (NYSE, NASDAQ, LSE, TSE, ASX): apertura exacta, cierre exacto, antes
 * y despues del horario, fin de semana y horario de verano (DST).
 *
 * <p>Complementa a {@link MercadoTest}, que concentra el detalle en NYSE. Aqui
 * cada escenario se valida contra TODOS los mercados usando sus horarios y
 * zonas horarias IANA reales (los mismos del catalogo del sistema), de modo que
 * los instantes se construyen en la zona local de cada mercado y los offsets
 * (incluido DST) los resuelve la zona IANA automaticamente.</p>
 */
@DisplayName("Mercado - cobertura por mercado y escenario")
class MercadoCoberturaMercadosTest {

    /**
     * Definicion de cada mercado con su zona y horario, alineada con
     * MercadoCatalogoAdapter. {@code observaDst} indica si la zona cambia de
     * offset entre enero y julio (EEUU, Londres y Sidney si; Tokio no).
     */
    private enum MercadoFixture {
        NYSE(CodigoMercado.NYSE, "America/New_York", LocalTime.of(9, 30), LocalTime.of(16, 0), true),
        NASDAQ(CodigoMercado.NASDAQ, "America/New_York", LocalTime.of(9, 30), LocalTime.of(16, 0), true),
        LSE(CodigoMercado.LSE, "Europe/London", LocalTime.of(8, 0), LocalTime.of(16, 30), true),
        TSE(CodigoMercado.TSE, "Asia/Tokyo", LocalTime.of(9, 0), LocalTime.of(15, 0), false),
        ASX(CodigoMercado.ASX, "Australia/Sydney", LocalTime.of(10, 0), LocalTime.of(16, 0), true);

        private final CodigoMercado codigo;
        private final ZoneId zona;
        private final LocalTime apertura;
        private final LocalTime cierre;
        private final boolean observaDst;

        MercadoFixture(CodigoMercado codigo, String zona, LocalTime apertura,
                       LocalTime cierre, boolean observaDst) {
            this.codigo = codigo;
            this.zona = ZoneId.of(zona);
            this.apertura = apertura;
            this.cierre = cierre;
            this.observaDst = observaDst;
        }

        Mercado crear() {
            return crear(CalendarioFeriados.vacio());
        }

        Mercado crear(CalendarioFeriados feriados) {
            return Mercado.conDiasHabilesEstandar(codigo, codigo.name(), zona,
                    HorarioOperacion.de(apertura, cierre), feriados);
        }

        /** Instante absoluto en la zona del mercado para la fecha/hora locales dadas. */
        Instant en(LocalDate fecha, LocalTime hora) {
            return ZonedDateTime.of(fecha, hora, zona).toInstant();
        }
    }

    /** Lunes 5 de enero de 2026 (dia habil, invierno boreal / verano austral). */
    private static final LocalDate LUNES_ENERO = LocalDate.of(2026, 1, 5);
    /** Lunes 6 de julio de 2026 (dia habil, verano boreal / invierno austral). */
    private static final LocalDate LUNES_JULIO = LocalDate.of(2026, 7, 6);
    /** Sabado 3 de enero de 2026. */
    private static final LocalDate SABADO = LocalDate.of(2026, 1, 3);
    /** Domingo 4 de enero de 2026. */
    private static final LocalDate DOMINGO = LocalDate.of(2026, 1, 4);

    @Nested
    @DisplayName("Apertura exacta")
    class AperturaExacta {
        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("en el instante exacto de apertura el mercado esta ABIERTO")
        void aperturaExacta(MercadoFixture m) {
            EstadoMercadoSnapshot estado = m.crear().evaluarEstado(m.en(LUNES_ENERO, m.apertura));
            assertThat(estado.abierto()).isTrue();
            assertThat(estado.estado()).isEqualTo(EstadoMercado.ABIERTO);
            assertThat(estado.proximoCierre()).contains(m.en(LUNES_ENERO, m.cierre));
        }
    }

    @Nested
    @DisplayName("Cierre exacto")
    class CierreExacto {
        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("en el instante exacto de cierre el mercado esta CERRADO (cierre exclusivo)")
        void cierreExacto(MercadoFixture m) {
            EstadoMercadoSnapshot estado = m.crear().evaluarEstado(m.en(LUNES_ENERO, m.cierre));
            assertThat(estado.abierto()).isFalse();
            // Abre el dia habil siguiente (martes 2026-01-06).
            assertThat(estado.proximaApertura())
                    .contains(m.en(LUNES_ENERO.plusDays(1), m.apertura));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("un minuto antes del cierre el mercado sigue ABIERTO")
        void unMinutoAntesDelCierre(MercadoFixture m) {
            EstadoMercadoSnapshot estado =
                    m.crear().evaluarEstado(m.en(LUNES_ENERO, m.cierre.minusMinutes(1)));
            assertThat(estado.abierto()).isTrue();
            assertThat(estado.proximoCierre()).contains(m.en(LUNES_ENERO, m.cierre));
        }
    }

    @Nested
    @DisplayName("Antes del horario")
    class AntesDelHorario {
        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("antes de la apertura el mercado esta CERRADO y abre el mismo dia")
        void antesDeApertura(MercadoFixture m) {
            EstadoMercadoSnapshot estado =
                    m.crear().evaluarEstado(m.en(LUNES_ENERO, m.apertura.minusMinutes(30)));
            assertThat(estado.abierto()).isFalse();
            assertThat(estado.proximaApertura()).contains(m.en(LUNES_ENERO, m.apertura));
        }
    }

    @Nested
    @DisplayName("Despues del horario")
    class DespuesDelHorario {
        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("despues del cierre el mercado esta CERRADO y abre el dia habil siguiente")
        void despuesDeCierre(MercadoFixture m) {
            EstadoMercadoSnapshot estado =
                    m.crear().evaluarEstado(m.en(LUNES_ENERO, m.cierre.plusMinutes(30)));
            assertThat(estado.abierto()).isFalse();
            assertThat(estado.proximaApertura())
                    .contains(m.en(LUNES_ENERO.plusDays(1), m.apertura));
        }
    }

    @Nested
    @DisplayName("Fin de semana")
    class FinDeSemana {
        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("el sabado en horario comercial el mercado esta CERRADO y abre el lunes")
        void sabado(MercadoFixture m) {
            EstadoMercadoSnapshot estado =
                    m.crear().evaluarEstado(m.en(SABADO, LocalTime.of(12, 0)));
            assertThat(estado.abierto()).isFalse();
            assertThat(estado.proximaApertura()).contains(m.en(LUNES_ENERO, m.apertura));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("el domingo el mercado esta CERRADO y abre el lunes")
        void domingo(MercadoFixture m) {
            EstadoMercadoSnapshot estado =
                    m.crear().evaluarEstado(m.en(DOMINGO, LocalTime.of(12, 0)));
            assertThat(estado.abierto()).isFalse();
            assertThat(estado.proximaApertura()).contains(m.en(LUNES_ENERO, m.apertura));
        }
    }

    @Nested
    @DisplayName("Horario de verano (DST)")
    class HorarioDeVerano {
        /**
         * La apertura siempre ocurre a la hora LOCAL configurada, tanto en
         * enero como en julio. La prueba valida que, pese al cambio de offset,
         * el mercado abre a su hora local; y que en los mercados con DST el
         * instante UTC de apertura difiere entre enero y julio, mientras que en
         * el mercado sin DST (TSE) es el mismo offset.
         */
        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("la apertura se mantiene en la hora local en enero y julio")
        void aperturaLocalEstableEntreTemporadas(MercadoFixture m) {
            Instant aperturaEnero =
                    m.crear().evaluarEstado(m.en(LUNES_ENERO, m.apertura.minusMinutes(1)))
                            .proximaApertura().orElseThrow();
            Instant aperturaJulio =
                    m.crear().evaluarEstado(m.en(LUNES_JULIO, m.apertura.minusMinutes(1)))
                            .proximaApertura().orElseThrow();

            // En ambos casos la apertura cae a la hora local configurada.
            assertThat(aperturaEnero.atZone(m.zona).toLocalTime()).isEqualTo(m.apertura);
            assertThat(aperturaJulio.atZone(m.zona).toLocalTime()).isEqualTo(m.apertura);

            int offsetEnero = aperturaEnero.atZone(m.zona).getOffset().getTotalSeconds();
            int offsetJulio = aperturaJulio.atZone(m.zona).getOffset().getTotalSeconds();
            if (m.observaDst) {
                assertThat(offsetEnero).isNotEqualTo(offsetJulio);
            } else {
                assertThat(offsetEnero).isEqualTo(offsetJulio);
            }
        }

        @Test
        @DisplayName("NYSE: 09:30 local pasa de 14:30Z (EST) a 13:30Z (EDT) tras iniciar DST")
        void nyseCambioDst() {
            MercadoFixture m = MercadoFixture.NYSE;
            // Dia habil en EST (enero) vs dia habil en EDT (julio).
            Instant est = m.crear().evaluarEstado(m.en(LUNES_ENERO, m.apertura.minusMinutes(1)))
                    .proximaApertura().orElseThrow();
            Instant edt = m.crear().evaluarEstado(m.en(LUNES_JULIO, m.apertura.minusMinutes(1)))
                    .proximaApertura().orElseThrow();
            assertThat(est).isEqualTo(Instant.parse("2026-01-05T14:30:00Z"));
            assertThat(edt).isEqualTo(Instant.parse("2026-07-06T13:30:00Z"));
        }

        @Test
        @DisplayName("ASX (hemisferio sur): en enero esta en horario de verano austral (AEDT)")
        void asxHorarioVeranoAustral() {
            MercadoFixture m = MercadoFixture.ASX;
            // En verano austral (enero) Sidney esta en AEDT (UTC+11): 10:00 local = 23:00Z del dia previo.
            Instant aperturaEnero = m.crear()
                    .evaluarEstado(m.en(LUNES_ENERO, m.apertura.minusMinutes(1)))
                    .proximaApertura().orElseThrow();
            assertThat(aperturaEnero).isEqualTo(Instant.parse("2026-01-04T23:00:00Z"));
            // En invierno austral (julio) es AEST (UTC+10): 10:00 local = 00:00Z del mismo dia.
            Instant aperturaJulio = m.crear()
                    .evaluarEstado(m.en(LUNES_JULIO, m.apertura.minusMinutes(1)))
                    .proximaApertura().orElseThrow();
            assertThat(aperturaJulio).isEqualTo(Instant.parse("2026-07-06T00:00:00Z"));
        }
    }

    @Nested
    @DisplayName("Feriado configurable por mercado")
    class Feriado {
        @ParameterizedTest(name = "{0}")
        @EnumSource(MercadoFixture.class)
        @DisplayName("en un feriado el mercado esta CERRADO con restriccion FERIADO")
        void feriado(MercadoFixture m) {
            CalendarioFeriados feriados = CalendarioFeriados.builder()
                    .agregarFeriado(m.codigo, LUNES_ENERO, "Feriado de prueba")
                    .construir();
            EstadoMercadoSnapshot estado =
                    m.crear(feriados).evaluarEstado(m.en(LUNES_ENERO, m.apertura.plusHours(1)));
            assertThat(estado.abierto()).isFalse();
            assertThat(estado.restricciones()).isNotEmpty();
            assertThat(estado.restricciones().get(0).codigo()).isEqualTo("FERIADO");
        }
    }

    @Test
    @DisplayName("sanity: el proximo lunes habil se calcula correctamente para el fixture")
    void sanityProximoLunes() {
        // Verifica que las constantes de fecha usadas son coherentes (SABADO/DOMINGO -> LUNES_ENERO).
        assertThat(SABADO.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(DOMINGO.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(SABADO.with(TemporalAdjusters.next(DayOfWeek.MONDAY))).isEqualTo(LUNES_ENERO);
        assertThat(LUNES_ENERO.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(LUNES_JULIO.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }
}
