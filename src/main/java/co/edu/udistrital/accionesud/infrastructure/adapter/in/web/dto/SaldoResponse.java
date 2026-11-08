package co.edu.udistrital.accionesud.infrastructure.adapter.in.web.dto;

import co.edu.udistrital.accionesud.domain.model.Saldo;
import co.edu.udistrital.accionesud.infrastructure.adapter.in.web.util.MoneyFormatter;

/**
 * Respuesta JSON del endpoint GET /api/v1/cuenta/saldo.
 * Todos los montos se exponen como texto con 2 decimales.
 */
public class SaldoResponse {

    private final String disponible;
    private final String reservado;
    private final String total;

    public SaldoResponse(String disponible, String reservado, String total) {
        this.disponible = disponible;
        this.reservado = reservado;
        this.total = total;
    }

    public static SaldoResponse desde(Saldo saldo) {
        return new SaldoResponse(
                MoneyFormatter.format(saldo.getDisponible()),
                MoneyFormatter.format(saldo.getReservado()),
                MoneyFormatter.format(saldo.getTotal()));
    }

    public String getDisponible() {
        return disponible;
    }

    public String getReservado() {
        return reservado;
    }

    public String getTotal() {
        return total;
    }
}
