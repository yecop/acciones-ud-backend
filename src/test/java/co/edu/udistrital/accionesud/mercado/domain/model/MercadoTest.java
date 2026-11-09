package co.edu.udistrital.accionesud.mercado.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias del agregado de dominio {@link Mercado}.
 *
 * <p>Se usa NYSE (9:30-16:00, America/New_York) salvo indicación contraria.
 * Los instantes se construyen explícitamente en la zona del mercado para
 * validar zonas horarias IANA, fines de semana y cambios de horario de verano.</p>
 */
class MercadoTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    private Mercado nyse() {
        return nyse(CalendarioFeriados.vacio());
    }

    private Mercado nyse(CalendarioFeriados feriados) {
        return Mercado.conDiasHabilesEstandar(
                CodigoMercado.NYSE,
                "New York Stock Exchange",
                NY,
                HorarioOperacion.de(LocalTime.of(9, 30), LocalTime.of(16, 0)),
                feriados);
    }

    private Instant instanteNy(int anio, int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(anio, mes, dia, hora, minuto, 0, 0, NY).toInstant();
    }

    @Nested
    @DisplayName("Apertura y cierre exactos")
    class AperturaYCierre {

        @Test
        @DisplayName("En el instante exacto de apertura el mercado está ABIERTO")
        void aperturaExacta() {
            // Lunes 2026-01-05, 09:30 (día hábil, no feriado)
            Instant instante = instanteNy(2026, 1, 5, 9, 30);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isTrue();
            assertThat(estado.estado()).isEqualTo(EstadoMercado.ABIERTO);
            assertThat(estado.proximoCierre()).isPresent();
            assertThat(estado.proximaApertura()).isEmpty();
        }

        @Test
        @DisplayName("En el instante exacto de cierre el mercado está CERRADO")
        void cierreExacto() {
            // Lunes 2026-01-05, 16:00 → el cierre es exclusivo
            Instant instante = instanteNy(2026, 1, 5, 16, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isFalse();
            // Próxima apertura: martes 2026-01-06 09:30
            assertThat(estado.proximaApertura()).contains(instanteNy(2026, 1, 6, 9, 30));
        }

        @Test
        @DisplayName("Un minuto antes del cierre el mercado sigue ABIERTO")
        void unMinutoAntesDelCierre() {
            Instant instante = instanteNy(2026, 1, 5, 15, 59);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isTrue();
            assertThat(estado.proximoCierre()).contains(instanteNy(2026, 1, 5, 16, 0));
        }
    }

    @Nested
    @DisplayName("Antes y después del horario comercial")
    class FueraDeHorario {

        @Test
        @DisplayName("Antes de la apertura el mercado está CERRADO y abre el mismo día")
        void antesDeApertura() {
            Instant instante = instanteNy(2026, 1, 5, 8, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isFalse();
            assertThat(estado.proximaApertura()).contains(instanteNy(2026, 1, 5, 9, 30));
        }

        @Test
        @DisplayName("Después del cierre el mercado está CERRADO y abre al día hábil siguiente")
        void despuesDeCierre() {
            Instant instante = instanteNy(2026, 1, 5, 18, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isFalse();
            assertThat(estado.proximaApertura()).contains(instanteNy(2026, 1, 6, 9, 30));
        }
    }

    @Nested
    @DisplayName("Fines de semana")
    class FinesDeSemana {

        @Test
        @DisplayName("Sábado en horario comercial el mercado está CERRADO")
        void sabado() {
            // Sábado 2026-01-03, 12:00
            Instant instante = instanteNy(2026, 1, 3, 12, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isFalse();
            // Próxima apertura: lunes 2026-01-05 09:30
            assertThat(estado.proximaApertura()).contains(instanteNy(2026, 1, 5, 9, 30));
        }

        @Test
        @DisplayName("Domingo el mercado está CERRADO y abre el lunes")
        void domingo() {
            // Domingo 2026-01-04, 10:00
            Instant instante = instanteNy(2026, 1, 4, 10, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isFalse();
            assertThat(estado.proximaApertura()).contains(instanteNy(2026, 1, 5, 9, 30));
        }

        @Test
        @DisplayName("Viernes después del cierre salta al lunes (omite fin de semana)")
        void viernesDespuesDelCierre() {
            // Viernes 2026-01-09, 17:00
            Instant instante = instanteNy(2026, 1, 9, 17, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isFalse();
            // Lunes 2026-01-12 09:30
            assertThat(estado.proximaApertura()).contains(instanteNy(2026, 1, 12, 9, 30));
        }
    }

    @Nested
    @DisplayName("Feriados configurables")
    class Feriados {

        @Test
        @DisplayName("En un feriado el mercado está CERRADO con restricción vigente")
        void feriado() {
            CalendarioFeriados feriados = CalendarioFeriados.builder()
                    .agregarFeriado(CodigoMercado.NYSE,
                            java.time.LocalDate.of(2026, 1, 5), "Feriado de prueba")
                    .construir();
            // Lunes 2026-01-05 en horario comercial, pero es feriado
            Instant instante = instanteNy(2026, 1, 5, 11, 0);

            EstadoMercadoSnapshot estado = nyse(feriados).evaluarEstado(instante);

            assertThat(estado.abierto()).isFalse();
            assertThat(estado.restricciones()).hasSize(1);
            assertThat(estado.restricciones().get(0).codigo()).isEqualTo("FERIADO");
            // Próxima apertura: martes 2026-01-06 09:30
            assertThat(estado.proximaApertura()).contains(instanteNy(2026, 1, 6, 9, 30));
        }
    }

    @Nested
    @DisplayName("Cambios de horario de verano (DST)")
    class HorarioDeVerano {

        @Test
        @DisplayName("La apertura mantiene las 9:30 locales antes del cambio a DST (EST)")
        void antesDelCambioDst() {
            // 2026-03-06 es viernes, aún en EST (UTC-5). 08:00 local → cerrado, abre 09:30.
            Instant instante = instanteNy(2026, 3, 6, 8, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            Instant aperturaEsperada = ZonedDateTime.of(2026, 3, 6, 9, 30, 0, 0, NY).toInstant();
            assertThat(estado.proximaApertura()).contains(aperturaEsperada);
            // En EST, 09:30 local equivale a 14:30 UTC
            assertThat(aperturaEsperada).isEqualTo(Instant.parse("2026-03-06T14:30:00Z"));
        }

        @Test
        @DisplayName("Tras iniciar DST (EDT), 09:30 local corresponde a un instante UTC distinto")
        void despuesDelCambioDst() {
            // DST en EE. UU. inicia el domingo 2026-03-08. El lunes 2026-03-09 ya es EDT (UTC-4).
            Instant instante = instanteNy(2026, 3, 9, 8, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            Instant aperturaEsperada = ZonedDateTime.of(2026, 3, 9, 9, 30, 0, 0, NY).toInstant();
            assertThat(estado.proximaApertura()).contains(aperturaEsperada);
            // En EDT, 09:30 local equivale a 13:30 UTC (una hora menos que en EST)
            assertThat(aperturaEsperada).isEqualTo(Instant.parse("2026-03-09T13:30:00Z"));
        }

        @Test
        @DisplayName("Durante el fin de semana del cambio a DST el mercado está CERRADO")
        void finDeSemanaDelCambio() {
            // Domingo 2026-03-08 (día del cambio), 10:00 local
            Instant instante = instanteNy(2026, 3, 8, 10, 0);

            EstadoMercadoSnapshot estado = nyse().evaluarEstado(instante);

            assertThat(estado.abierto()).isFalse();
            // Abre lunes 2026-03-09 09:30 (ya en EDT)
            assertThat(estado.proximaApertura()).contains(Instant.parse("2026-03-09T13:30:00Z"));
        }
    }

    @Nested
    @DisplayName("Otros mercados y sus zonas horarias")
    class OtrosMercados {

        @Test
        @DisplayName("TSE (Asia/Tokyo) abierto a las 10:00 locales")
        void tseAbierto() {
            ZoneId tokyo = ZoneId.of("Asia/Tokyo");
            Mercado tse = Mercado.conDiasHabilesEstandar(CodigoMercado.TSE, "Tokyo",
                    tokyo, HorarioOperacion.de(LocalTime.of(9, 0), LocalTime.of(15, 0)),
                    CalendarioFeriados.vacio());

            // Lunes 2026-01-05 10:00 en Tokio
            Instant instante = ZonedDateTime.of(2026, 1, 5, 10, 0, 0, 0, tokyo).toInstant();

            assertThat(tse.evaluarEstado(instante).abierto()).isTrue();
        }

        @Test
        @DisplayName("ASX (Australia/Sydney) cerrado a las 9:00 locales (antes de apertura 10:00)")
        void asxCerrado() {
            ZoneId sydney = ZoneId.of("Australia/Sydney");
            Mercado asx = Mercado.conDiasHabilesEstandar(CodigoMercado.ASX, "Sydney",
                    sydney, HorarioOperacion.de(LocalTime.of(10, 0), LocalTime.of(16, 0)),
                    CalendarioFeriados.vacio());

            Instant instante = ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, sydney).toInstant();

            assertThat(asx.evaluarEstado(instante).abierto()).isFalse();
        }
    }
}
