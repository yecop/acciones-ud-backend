package co.edu.udistrital.accionesud.mercado.domain.model;

/**
 * Códigos de los mercados soportados por la plataforma.
 */
public enum CodigoMercado {
    NYSE,
    NASDAQ,
    LSE,
    TSE,
    ASX;

    public static CodigoMercado desde(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El código de mercado no puede ser nulo");
        }
        return CodigoMercado.valueOf(valor.trim().toUpperCase());
    }
}
