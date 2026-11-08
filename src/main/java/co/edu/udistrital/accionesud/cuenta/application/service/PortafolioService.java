package co.edu.udistrital.accionesud.cuenta.application.service;

import co.edu.udistrital.accionesud.cuenta.application.dto.PosicionValorada;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResumenPortafolio;
import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarPortafolioUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PortafolioRepositoryPort;
import co.edu.udistrital.accionesud.cuenta.application.port.out.PrecioCachePort;
import co.edu.udistrital.accionesud.cuenta.domain.model.Portafolio;
import co.edu.udistrital.accionesud.cuenta.domain.model.Posicion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de aplicacion que implementa el caso de uso de consulta de portafolio.
 *
 * <p>Orquesta el repositorio de portafolio (posiciones) y la cache de precios
 * para valorar cada posicion y calcular el resumen. La aritmetica financiera
 * reside en el dominio ({@link Posicion} y {@link Portafolio}); este servicio
 * solo coordina.
 *
 * <p>Regla de negocio SCRUM-25: un usuario nuevo (sin portafolio) obtiene un
 * portafolio vacio, lo que produce un resumen en ceros y una lista de
 * posiciones vacia.
 */
public class PortafolioService implements ConsultarPortafolioUseCase {

    private final PortafolioRepositoryPort portafolioRepository;
    private final PrecioCachePort precioCache;

    public PortafolioService(PortafolioRepositoryPort portafolioRepository,
                             PrecioCachePort precioCache) {
        this.portafolioRepository =
                Objects.requireNonNull(portafolioRepository, "portafolioRepository");
        this.precioCache = Objects.requireNonNull(precioCache, "precioCache");
    }

    @Override
    public ResumenPortafolio consultarResumen(String usuarioId) {
        Portafolio portafolio = obtenerPortafolio(usuarioId);

        BigDecimal valorTotal = portafolio.valorTotal(precioCache::obtenerPrecioActual);
        BigDecimal costoTotal = portafolio.costoTotal();
        BigDecimal gananciaPerdidaTotal =
                portafolio.gananciaPerdidaTotal(precioCache::obtenerPrecioActual);
        BigDecimal porcentajeTotal = calcularPorcentaje(gananciaPerdidaTotal, costoTotal);

        return new ResumenPortafolio(
                valorTotal,
                costoTotal,
                gananciaPerdidaTotal,
                porcentajeTotal,
                portafolio.getPosiciones().size());
    }

    @Override
    public List<PosicionValorada> consultarPosiciones(String usuarioId) {
        Portafolio portafolio = obtenerPortafolio(usuarioId);

        List<PosicionValorada> resultado = new ArrayList<>();
        for (Posicion posicion : portafolio.getPosiciones()) {
            BigDecimal precioActual = precioCache.obtenerPrecioActual(posicion.getActivo());
            resultado.add(new PosicionValorada(
                    posicion.getActivo(),
                    posicion.getCantidad(),
                    posicion.getPrecioCompra(),
                    precioActual,
                    posicion.valorActual(precioActual),
                    posicion.gananciaPerdida(precioActual),
                    posicion.porcentajeGananciaPerdida(precioActual)));
        }
        return resultado;
    }

    private Portafolio obtenerPortafolio(String usuarioId) {
        Objects.requireNonNull(usuarioId, "El usuarioId no puede ser nulo");
        return portafolioRepository.buscarPorUsuario(usuarioId)
                .orElseGet(() -> Portafolio.vacio(usuarioId));
    }

    private BigDecimal calcularPorcentaje(BigDecimal gananciaPerdida, BigDecimal costoBase) {
        if (costoBase == null || costoBase.signum() == 0) {
            return BigDecimal.ZERO.setScale(Posicion.ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
        }
        return gananciaPerdida
                .multiply(BigDecimal.valueOf(100))
                .divide(costoBase, Posicion.ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
    }
}
