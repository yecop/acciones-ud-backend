package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.cuenta.application.port.out.SaldoRepositoryPort;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de salida en memoria para el saldo.
 *
 * <p>Simula la persistencia: los usuarios que no existen en el mapa se
 * consideran "nuevos", por lo que el servicio de aplicacion les asigna el saldo
 * inicial simulado.
 */
@Repository
public class InMemorySaldoRepository implements SaldoRepositoryPort {

    private final Map<String, Saldo> saldosPorUsuario = new ConcurrentHashMap<>();

    @Override
    public Optional<Saldo> buscarPorUsuario(String usuarioId) {
        return Optional.ofNullable(saldosPorUsuario.get(usuarioId));
    }

    /** Utilidad para precargar saldos (uso en pruebas o seeds). */
    public void guardar(String usuarioId, Saldo saldo) {
        saldosPorUsuario.put(usuarioId, saldo);
    }
}
