package co.edu.udistrital.accionesud.infrastructure.adapter.in.web.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utilidad del adaptador web para formatear montos como texto con exactamente
 * 2 decimales y punto como separador decimal (ej. "1500.00"), segun lo exige
 * SCRUM-25.
 */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    /**
     * Formatea un {@link BigDecimal} como cadena con 2 decimales.
     * Un valor nulo se formatea como "0.00".
     */
    public static String format(BigDecimal valor) {
        BigDecimal seguro = valor == null ? BigDecimal.ZERO : valor;
        return seguro.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
