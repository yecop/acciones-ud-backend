package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.mercado.domain.model.EstadoMercadoSnapshot;
import co.edu.udistrital.accionesud.mercado.domain.model.Mercado;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.EstadoMercadoResponse;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.MercadoResponse;
import co.edu.udistrital.accionesud.mercado.infrastructure.adapter.in.rest.dto.RestriccionResponse;

import java.util.List;

/**
 * Mapea los modelos de dominio a los DTO de respuesta REST.
 */
final class MercadoRestMapper {

    private MercadoRestMapper() {
    }

    static MercadoResponse aMercadoResponse(Mercado mercado) {
        return new MercadoResponse(
                mercado.codigo().name(),
                mercado.nombre(),
                mercado.zonaHoraria().getId(),
                mercado.horario().apertura().toString(),
                mercado.horario().cierre().toString(),
                "LUNES_A_VIERNES");
    }

    static EstadoMercadoResponse aEstadoResponse(EstadoMercadoSnapshot snapshot) {
        List<RestriccionResponse> restricciones = snapshot.restricciones().stream()
                .map(r -> new RestriccionResponse(r.codigo(), r.descripcion()))
                .toList();

        return new EstadoMercadoResponse(
                snapshot.codigo().name(),
                snapshot.estado().name(),
                snapshot.abierto(),
                snapshot.horaConsulta().getZone().getId(),
                snapshot.horaConsulta().toInstant(),
                snapshot.proximaApertura().orElse(null),
                snapshot.proximoCierre().orElse(null),
                restricciones);
    }
}
