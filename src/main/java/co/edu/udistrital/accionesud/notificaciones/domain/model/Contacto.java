package co.edu.udistrital.accionesud.notificaciones.domain.model;

import java.util.Optional;

/** Datos para entregar una notificación por canales externos; cualquiera puede faltar. */
public record Contacto(String correo, String celular) {

    public Contacto {
        correo = limpiar(correo);
        celular = limpiar(celular);
    }

    public Optional<String> correoOpcional() {
        return Optional.ofNullable(correo);
    }

    public Optional<String> celularOpcional() {
        return Optional.ofNullable(celular);
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
