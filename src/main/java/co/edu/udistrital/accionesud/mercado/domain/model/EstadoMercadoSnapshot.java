package co.edu.udistrital.accionesud.mercado.domain.model;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado inmutable de evaluar el estado de un mercado en un instante.
 */
public final class EstadoMercadoSnapshot {

    private final CodigoMercado codigo;
    private final EstadoMercado estado;
    private final ZonedDateTime horaConsulta;
    private final Instant proximaApertura;
    private final Instant proximoCierre;
    private final List<Restriccion> restricciones;

    public EstadoMercadoSnapshot(CodigoMercado codigo,
                                 EstadoMercado estado,
                                 ZonedDateTime horaConsulta,
                                 Instant proximaApertura,
                                 Instant proximoCierre,
                                 List<Restriccion> restricciones) {
        this.codigo = Objects.requireNonNull(codigo);
        this.estado = Objects.requireNonNull(estado);
        this.horaConsulta = Objects.requireNonNull(horaConsulta);
        this.proximaApertura = proximaApertura;
        this.proximoCierre = proximoCierre;
        this.restricciones = restricciones == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(restricciones));
    }

    public CodigoMercado codigo() {
        return codigo;
    }

    public EstadoMercado estado() {
        return estado;
    }

    public boolean abierto() {
        return estado == EstadoMercado.ABIERTO;
    }

    public ZonedDateTime horaConsulta() {
        return horaConsulta;
    }

    public Optional<Instant> proximaApertura() {
        return Optional.ofNullable(proximaApertura);
    }

    public Optional<Instant> proximoCierre() {
        return Optional.ofNullable(proximoCierre);
    }

    public List<Restriccion> restricciones() {
        return restricciones;
    }
}
