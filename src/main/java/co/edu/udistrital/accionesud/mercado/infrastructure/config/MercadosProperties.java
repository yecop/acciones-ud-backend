package co.edu.udistrital.accionesud.mercado.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Propiedades configurables del catálogo de mercados y su lista de feriados.
 *
 * <p>Se enlazan desde {@code application.yml} bajo el prefijo
 * {@code accionesud.mercados}. Esto permite administrar feriados y horarios
 * sin modificar el código de dominio.</p>
 */
@ConfigurationProperties(prefix = "accionesud.mercados")
public class MercadosProperties {

    /** Lista de feriados configurables por mercado. */
    private List<Feriado> feriados = new ArrayList<>();

    public List<Feriado> getFeriados() {
        return feriados;
    }

    public void setFeriados(List<Feriado> feriados) {
        this.feriados = feriados;
    }

    public static class Feriado {
        /** Código de mercado (NYSE, NASDAQ, LSE, TSE, ASX). */
        private String mercado;
        /** Fecha del feriado en formato ISO (yyyy-MM-dd). */
        private String fecha;
        /** Descripción del feriado. */
        private String descripcion;

        public String getMercado() {
            return mercado;
        }

        public void setMercado(String mercado) {
            this.mercado = mercado;
        }

        public String getFecha() {
            return fecha;
        }

        public void setFecha(String fecha) {
            this.fecha = fecha;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public void setDescripcion(String descripcion) {
            this.descripcion = descripcion;
        }
    }
}
