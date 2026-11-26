package co.edu.udistrital.accionesud.notificaciones.domain.model;

import co.edu.udistrital.accionesud.shared.domain.evento.EventoDominio;
import co.edu.udistrital.accionesud.shared.domain.evento.Eventos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Convierte los eventos de dominio (SCRUM-33) en avisos para los usuarios (SCRUM-51).
 * Un evento que no interesa al usuario devuelve una lista vacía.
 */
public final class TraductorEventos {

    public List<Aviso> traducir(EventoDominio evento) {
        if (evento instanceof Eventos.OrdenEjecutada e) {
            if (!e.completa() || e.cuentaId() == null) {
                return List.of(); // Una ejecución parcial no notifica: solo la orden completa.
            }
            return List.of(new Aviso(new Aviso.TitularDeCuenta(e.cuentaId()), TipoNotificacion.ORDEN_EJECUTADA,
                    "Orden ejecutada",
                    "Tu orden de " + e.ticker() + " se ejecutó: " + e.cantidadEjecutada() + " a "
                            + monto(e.precioEjecucion()) + ".",
                    e.ordenId().toString()));
        }
        if (evento instanceof Eventos.OrdenEstadoCambiado e && "CANCELADA".equals(e.estadoNuevo())
                && e.cuentaId() != null) {
            String motivo = e.motivo() == null || e.motivo().isBlank() ? "" : " Motivo: " + e.motivo() + ".";
            return List.of(new Aviso(new Aviso.TitularDeCuenta(e.cuentaId()), TipoNotificacion.ORDEN_CANCELADA,
                    "Orden cancelada", "Tu orden fue cancelada." + motivo, e.ordenId().toString()));
        }
        if (evento instanceof Eventos.MovimientoFondosRegistrado e) {
            String tipo = e.tipo() == null ? "Movimiento" : capitalizar(e.tipo());
            return List.of(new Aviso(new Aviso.TitularDeCuenta(e.cuentaId()), TipoNotificacion.MOVIMIENTO_FONDOS,
                    tipo + " registrado",
                    tipo + " por " + monto(e.monto()) + ". Saldo disponible: " + monto(e.saldoDisponible()) + ".",
                    null));
        }
        if (evento instanceof Eventos.AlertaPrecioDisparada e) {
            return List.of(new Aviso(new Aviso.Usuario(e.usuarioId()), TipoNotificacion.ALERTA_PRECIO,
                    "Alerta de " + e.ticker(),
                    e.ticker() + " cumplió tu alerta (" + e.condicion() + "). Precio: " + monto(e.precio()) + ".",
                    e.alertaId().toString()));
        }
        if (evento instanceof Eventos.PropuestaOrdenesCreada e && e.inversionistaId() != null) {
            return List.of(new Aviso(new Aviso.Usuario(e.inversionistaId()), TipoNotificacion.PROPUESTA_ORDENES,
                    "Nueva propuesta de tu comisionista",
                    "Tienes una propuesta con " + e.numeroOrdenes()
                            + (e.numeroOrdenes() == 1 ? " orden" : " órdenes") + " para revisar.",
                    e.propuestaId().toString()));
        }
        if (evento instanceof Eventos.AsociacionComisionistaCambiada e) {
            String cambio = e.cambio().toLowerCase(Locale.ROOT);
            Aviso alInversionista = new Aviso(new Aviso.Usuario(e.inversionistaId()),
                    TipoNotificacion.ASOCIACION_COMISIONISTA, "Tu comisionista cambió",
                    "La relación con tu comisionista quedó " + cambio + ".", null);
            if (e.comisionistaId() == null) {
                return List.of(alInversionista);
            }
            Aviso alComisionista = new Aviso(new Aviso.Usuario(e.comisionistaId()),
                    TipoNotificacion.ASOCIACION_COMISIONISTA, "Cambio en tus clientes",
                    "La relación con un cliente quedó " + cambio + ".", e.inversionistaId().toString());
            return List.of(alInversionista, alComisionista);
        }
        if (evento instanceof Eventos.SuscripcionActualizada e) {
            return List.of(new Aviso(new Aviso.Usuario(e.usuarioId()), TipoNotificacion.SUSCRIPCION,
                    "Tu suscripción cambió",
                    "Plan " + e.plan() + ", estado " + e.estado() + ".", null));
        }
        return List.of();
    }

    private static String monto(BigDecimal valor) {
        return valor == null ? "—" : "$" + valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String capitalizar(String texto) {
        String minusculas = texto.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(minusculas.charAt(0)) + minusculas.substring(1);
    }
}
