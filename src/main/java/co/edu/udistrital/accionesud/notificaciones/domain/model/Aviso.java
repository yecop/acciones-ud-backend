package co.edu.udistrital.accionesud.notificaciones.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Lo que hay que notificar a partir de un evento, antes de saber el usuario concreto: el
 * destinatario puede venir como usuario o como cuenta de corretaje (se resuelve con un puerto).
 */
public record Aviso(Destinatario destinatario, TipoNotificacion tipo, String titulo, String mensaje,
                    String referenciaId) {

    public Aviso {
        Objects.requireNonNull(destinatario);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(titulo);
        Objects.requireNonNull(mensaje);
    }

    public sealed interface Destinatario permits Usuario, TitularDeCuenta {
    }

    public record Usuario(UUID usuarioId) implements Destinatario {
        public Usuario {
            Objects.requireNonNull(usuarioId);
        }
    }

    public record TitularDeCuenta(UUID cuentaId) implements Destinatario {
        public TitularDeCuenta {
            Objects.requireNonNull(cuentaId);
        }
    }
}
