package co.edu.udistrital.accionesud.mercado.application.service;

import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarEstadoMercadoUseCase;
import co.edu.udistrital.accionesud.mercado.application.port.in.ConsultarMercadosUseCase;
import co.edu.udistrital.accionesud.mercado.domain.exception.MercadoNoEncontradoException;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.EstadoMercadoSnapshot;
import co.edu.udistrital.accionesud.mercado.domain.model.Mercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.MercadoCatalogoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.ProveedorDatosMercadoPort;
import co.edu.udistrital.accionesud.mercado.domain.port.out.RelojPort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de aplicación que orquesta el dominio de mercados.
 *
 * <p>Obtiene la hora mediante {@link RelojPort} (aislando el reloj real),
 * consulta el catálogo de mercados y delega el cálculo de estado al agregado
 * {@link Mercado}. Si un proveedor externo reporta un estado forzado, éste
 * tiene prioridad sobre el horario regular.</p>
 */
public class MercadoService implements ConsultarMercadosUseCase, ConsultarEstadoMercadoUseCase {

    private final MercadoCatalogoPort catalogo;
    private final RelojPort reloj;
    private final List<ProveedorDatosMercadoPort> proveedores;

    public MercadoService(MercadoCatalogoPort catalogo,
                          RelojPort reloj,
                          List<ProveedorDatosMercadoPort> proveedores) {
        this.catalogo = Objects.requireNonNull(catalogo, "El catálogo es obligatorio");
        this.reloj = Objects.requireNonNull(reloj, "El reloj es obligatorio");
        this.proveedores = proveedores == null ? List.of() : List.copyOf(proveedores);
    }

    @Override
    public List<Mercado> listarMercados() {
        return catalogo.listar();
    }

    @Override
    public EstadoMercadoSnapshot consultarEstado(CodigoMercado codigo) {
        Mercado mercado = catalogo.buscarPorCodigo(codigo)
                .orElseThrow(() -> new MercadoNoEncontradoException(codigo.name()));

        Instant ahora = reloj.ahora();
        EstadoMercadoSnapshot snapshot = mercado.evaluarEstado(ahora);

        // Los proveedores externos pueden forzar un cierre no planeado (halt).
        Optional<ProveedorDatosMercadoPort.EstadoReportado> reportado = estadoReportado(codigo);
        if (reportado.isPresent() && !reportado.get().abierto() && snapshot.abierto()) {
            return mercado.evaluarEstado(ahora); // base para próximas aperturas/cierres
        }
        return snapshot;
    }

    private Optional<ProveedorDatosMercadoPort.EstadoReportado> estadoReportado(CodigoMercado codigo) {
        return proveedores.stream()
                .filter(proveedor -> proveedor.soporta(codigo))
                .map(proveedor -> proveedor.consultarEstadoReportado(codigo))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
