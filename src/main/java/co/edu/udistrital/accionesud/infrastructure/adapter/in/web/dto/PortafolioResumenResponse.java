package co.edu.udistrital.accionesud.infrastructure.adapter.in.web.dto;

import co.edu.udistrital.accionesud.application.dto.ResumenPortafolio;
import co.edu.udistrital.accionesud.infrastructure.adapter.in.web.util.MoneyFormatter;

/**
 * Respuesta JSON del endpoint GET /api/v1/portafolio.
 * Los montos se exponen como texto con 2 decimales.
 */
public class PortafolioResumenResponse {

    private final String valorTotal;
    private final String costoTotal;
    private final String gananciaPerdidaTotal;
    private final String porcentajeGananciaPerdidaTotal;
    private final int numeroPosiciones;

    public PortafolioResumenResponse(String valorTotal,
                                     String costoTotal,
                                     String gananciaPerdidaTotal,
                                     String porcentajeGananciaPerdidaTotal,
                                     int numeroPosiciones) {
        this.valorTotal = valorTotal;
        this.costoTotal = costoTotal;
        this.gananciaPerdidaTotal = gananciaPerdidaTotal;
        this.porcentajeGananciaPerdidaTotal = porcentajeGananciaPerdidaTotal;
        this.numeroPosiciones = numeroPosiciones;
    }

    public static PortafolioResumenResponse desde(ResumenPortafolio resumen) {
        return new PortafolioResumenResponse(
                MoneyFormatter.format(resumen.getValorTotal()),
                MoneyFormatter.format(resumen.getCostoTotal()),
                MoneyFormatter.format(resumen.getGananciaPerdidaTotal()),
                MoneyFormatter.format(resumen.getPorcentajeGananciaPerdidaTotal()),
                resumen.getNumeroPosiciones());
    }

    public String getValorTotal() {
        return valorTotal;
    }

    public String getCostoTotal() {
        return costoTotal;
    }

    public String getGananciaPerdidaTotal() {
        return gananciaPerdidaTotal;
    }

    public String getPorcentajeGananciaPerdidaTotal() {
        return porcentajeGananciaPerdidaTotal;
    }

    public int getNumeroPosiciones() {
        return numeroPosiciones;
    }
}
