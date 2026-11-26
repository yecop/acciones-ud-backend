package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.out.persistence;

import co.edu.udistrital.accionesud.cuenta.application.dto.PaginaMovimientos;
import co.edu.udistrital.accionesud.cuenta.application.port.out.CuentaFondosPort;
import co.edu.udistrital.accionesud.cuenta.domain.exception.ConflictoIdempotenciaException;
import co.edu.udistrital.accionesud.cuenta.domain.model.MovimientoFondos;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.domain.model.TipoMovimiento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mismo contrato en memoria, para el perfil {@code test} (sin base de datos).
 *
 * <p>Sin tabla de usuarios, cada inversionista recibe una cuenta la primera vez que se consulta,
 * como hacía el saldo simulado de SCRUM-25. Los métodos son {@code synchronized}: el débito
 * condicional es atómico igual que en PostgreSQL.</p>
 */
public class CuentaFondosEnMemoriaAdapter implements CuentaFondosPort {

    private final Map<UUID, UUID> cuentasPorInversionista = new HashMap<>();
    private final Map<UUID, Saldo> saldos = new HashMap<>();
    private final Map<String, MovimientoFondos> porClave = new HashMap<>();
    private final List<MovimientoFondos> movimientos = new ArrayList<>();

    @Override
    public synchronized Optional<UUID> cuentaDe(UUID inversionistaId) {
        UUID cuenta = cuentasPorInversionista.computeIfAbsent(inversionistaId, id -> UUID.randomUUID());
        saldos.putIfAbsent(cuenta, new Saldo(BigDecimal.ZERO, BigDecimal.ZERO));
        return Optional.of(cuenta);
    }

    @Override
    public synchronized Saldo saldo(UUID cuentaId) {
        return saldos.getOrDefault(cuentaId, new Saldo(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Override
    public synchronized boolean aplicarSaldoInicial(MovimientoFondos deposito, String claveIdempotencia) {
        boolean tieneMovimientos = movimientos.stream().anyMatch(m -> m.cuentaId().equals(deposito.cuentaId()));
        if (tieneMovimientos || porClave.containsKey(claveIdempotencia)) {
            return false;
        }
        registrar(deposito, claveIdempotencia);
        acreditar(deposito.cuentaId(), deposito.monto());
        return true;
    }

    @Override
    public synchronized BigDecimal acreditar(UUID cuentaId, BigDecimal monto) {
        Saldo actual = saldo(cuentaId);
        Saldo nuevo = new Saldo(actual.getDisponible().add(monto), actual.getReservado());
        saldos.put(cuentaId, nuevo);
        return nuevo.getDisponible();
    }

    @Override
    public synchronized Optional<BigDecimal> debitar(UUID cuentaId, BigDecimal monto) {
        Saldo actual = saldo(cuentaId);
        if (actual.getDisponible().compareTo(monto) < 0) {
            return Optional.empty();
        }
        Saldo nuevo = new Saldo(actual.getDisponible().subtract(monto), actual.getReservado());
        saldos.put(cuentaId, nuevo);
        return Optional.of(nuevo.getDisponible());
    }

    @Override
    public synchronized void registrar(MovimientoFondos movimiento, String claveIdempotencia) {
        if (porClave.containsKey(claveIdempotencia)) {
            throw new ConflictoIdempotenciaException();
        }
        porClave.put(claveIdempotencia, movimiento);
        movimientos.add(movimiento);
    }

    @Override
    public synchronized Optional<MovimientoFondos> buscarPorClave(String claveIdempotencia) {
        return Optional.ofNullable(porClave.get(claveIdempotencia));
    }

    @Override
    public synchronized PaginaMovimientos listar(UUID cuentaId, TipoMovimiento tipo, int pagina, int tamano) {
        List<MovimientoFondos> filtrados = movimientos.stream()
                .filter(m -> m.cuentaId().equals(cuentaId))
                .filter(m -> tipo == null || m.tipo() == tipo)
                .sorted(Comparator.comparing(MovimientoFondos::creadoEn).reversed())
                .toList();
        int desde = (int) Math.min((long) pagina * tamano, filtrados.size());
        int hasta = Math.min(desde + tamano, filtrados.size());
        return PaginaMovimientos.de(filtrados.subList(desde, hasta), pagina, tamano, filtrados.size());
    }
}
