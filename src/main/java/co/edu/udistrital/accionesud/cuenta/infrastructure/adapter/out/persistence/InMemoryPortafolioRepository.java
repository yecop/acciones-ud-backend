package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.cuenta.application.port.out.PortafolioRepositoryPort;
import co.edu.udistrital.accionesud.cuenta.domain.model.Portafolio;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de salida en memoria para el portafolio.
 *
 * <p>Un usuario que no exista en el mapa se considera nuevo; el servicio de
 * aplicacion le entrega un portafolio vacio.
 */
@Repository
public class InMemoryPortafolioRepository implements PortafolioRepositoryPort {

    private final Map<String, Portafolio> portafoliosPorUsuario = new ConcurrentHashMap<>();

    @Override
    public Optional<Portafolio> buscarPorUsuario(String usuarioId) {
        return Optional.ofNullable(portafoliosPorUsuario.get(usuarioId));
    }

    /** Utilidad para precargar portafolios (uso en pruebas o seeds). */
    public void guardar(Portafolio portafolio) {
        portafoliosPorUsuario.put(portafolio.getUsuarioId(), portafolio);
    }
}
