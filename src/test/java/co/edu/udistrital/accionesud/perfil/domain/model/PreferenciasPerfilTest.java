package co.edu.udistrital.accionesud.perfil.domain.model;

import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferenciasPerfilTest {

    private PreferenciasPerfil con(TipoOrden tipo, boolean doble, BigDecimal umbral) {
        return new PreferenciasPerfil(TipoVisualizacion.LISTA, OrdenPortafolio.VALOR_TOTAL, false, tipo,
                null, doble, umbral, false, EnumSet.of(CanalNotificacion.EMAIL),
                EnumSet.noneOf(EventoNotificacion.class));
    }

    @Test
    void valoresPorDefectoSonValidos() {
        PreferenciasPerfil p = PreferenciasPerfil.porDefecto();
        assertEquals(TipoOrden.MARKET, p.tipoOrdenPredeterminado());
        assertTrue(p.canales().contains(CanalNotificacion.EMAIL));
        assertEquals(EnumSet.allOf(EventoNotificacion.class), p.eventos());
    }

    @Test
    void trailingStopEstaDeshabilitado() {
        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> con(TipoOrden.TRAILING_STOP, false, null));
        assertEquals(1, ex.getErrores().size());
        assertEquals("tipoOrdenPredeterminado", ex.getErrores().get(0).campo());
    }

    @Test
    void dobleAutorizacionExigeUmbral() {
        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> con(TipoOrden.LIMIT, true, null));
        assertEquals("umbralOrdenGrande", ex.getErrores().get(0).campo());
    }

    @Test
    void acumulaTodosLosErrores() {
        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> con(TipoOrden.TRAILING_STOP, true, null));
        assertEquals(2, ex.getErrores().size());
    }

    @Test
    void umbralSeNormalizaADosDecimales() {
        PreferenciasPerfil p = con(TipoOrden.LIMIT, true, new BigDecimal("5000"));
        assertEquals("5000.00", p.umbralOrdenGrande().toPlainString());
    }

    @Test
    void exigeAlMenosUnCanal() {
        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> new PreferenciasPerfil(TipoVisualizacion.LISTA, OrdenPortafolio.VALOR_TOTAL, false,
                        TipoOrden.MARKET, null, false, null, false,
                        EnumSet.noneOf(CanalNotificacion.class), EnumSet.noneOf(EventoNotificacion.class)));
        assertEquals("canales", ex.getErrores().get(0).campo());
    }
}