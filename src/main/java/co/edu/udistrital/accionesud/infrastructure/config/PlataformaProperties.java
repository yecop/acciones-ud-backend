package co.edu.udistrital.accionesud.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Propiedades parametrizables de la plataforma, enlazadas desde
 * application.yml bajo el prefijo "accionesud".
 *
 * <p>SCRUM-25: el saldo inicial simulado para usuarios nuevos es configurable
 * mediante {@code accionesud.saldo-inicial-simulado}.
 */
@ConfigurationProperties(prefix = "accionesud")
public class PlataformaProperties {

    /**
     * Saldo disponible inicial que se asigna a un usuario nuevo.
     * Valor por defecto: 100000.00.
     */
    private BigDecimal saldoInicialSimulado = new BigDecimal("100000.00");

    public BigDecimal getSaldoInicialSimulado() {
        return saldoInicialSimulado;
    }

    public void setSaldoInicialSimulado(BigDecimal saldoInicialSimulado) {
        this.saldoInicialSimulado = saldoInicialSimulado;
    }
}
