package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.application.dto.AccionConCotizacion;
import co.edu.udistrital.accionesud.mercado.application.dto.Pagina;
import co.edu.udistrital.accionesud.mercado.domain.model.Accion;
import co.edu.udistrital.accionesud.mercado.domain.model.Advertencia;
import co.edu.udistrital.accionesud.mercado.domain.model.Cotizacion;
import co.edu.udistrital.accionesud.mercado.domain.model.CotizacionVigente;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.AccionResponse;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.AdvertenciaResponse;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.CotizacionResponse;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.PaginaResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Traduce los modelos de dominio/aplicación al contrato REST de acciones.
 */
final class AccionesRestMapper {

    private AccionesRestMapper() {
    }

    static PaginaResponse<AccionResponse> aPaginaResponse(Pagina<AccionConCotizacion> pagina) {
        return new PaginaResponse<>(
                pagina.contenido().stream().map(AccionesRestMapper::aAccionResponse).toList(),
                pagina.pagina(), pagina.tamano(), pagina.totalElementos(), pagina.totalPaginas());
    }

    static AccionResponse aAccionResponse(AccionConCotizacion item) {
        Accion accion = item.accion();
        return new AccionResponse(
                accion.ticker(),
                accion.nombre(),
                accion.mercado().name(),
                accion.sector().name(),
                accion.moneda(),
                item.cotizacion() == null ? null : aCotizacionResponse(item.cotizacion()),
                aAdvertencia(item.advertencia()));
    }

    static CotizacionResponse aCotizacionResponse(CotizacionVigente vigente) {
        Cotizacion c = vigente.cotizacion();
        return new CotizacionResponse(
                c.ticker(),
                monto(c.precio()),
                monto(c.bid()),
                monto(c.ask()),
                monto(c.spread()),
                c.volumen(),
                c.actualizadoEn(),
                c.fuente(),
                vigente.antiguedadSegundos(),
                vigente.umbralAntiguedadSegundos(),
                vigente.desactualizada(),
                aAdvertencia(vigente.advertencia()));
    }

    private static AdvertenciaResponse aAdvertencia(Advertencia advertencia) {
        return advertencia == null ? null : new AdvertenciaResponse(advertencia.codigo(), advertencia.mensaje());
    }

    /** Montos del contrato: texto decimal con punto y dos decimales. */
    private static String monto(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
