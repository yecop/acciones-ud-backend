package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.catalogo;

import co.edu.udistrital.accionesud.mercado.domain.model.CalendarioFeriados;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.HorarioOperacion;
import co.edu.udistrital.accionesud.mercado.domain.model.Mercado;
import co.edu.udistrital.accionesud.mercado.domain.port.out.MercadoCatalogoPort;
import co.edu.udistrital.accionesud.mercado.infrastructure.config.MercadosProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador de salida que construye el catálogo de mercados soportados.
 *
 * <p>Los horarios y zonas horarias IANA están definidos según los requisitos de
 * negocio; los feriados se inyectan desde {@link MercadosProperties} (configurables).</p>
 */
@Component
public class MercadoCatalogoAdapter implements MercadoCatalogoPort {

    private final Map<CodigoMercado, Mercado> mercados;

    public MercadoCatalogoAdapter(MercadosProperties properties) {
        CalendarioFeriados feriados = construirFeriados(properties);
        this.mercados = new EnumMap<>(CodigoMercado.class);

        registrar(Mercado.conDiasHabilesEstandar(CodigoMercado.NYSE, "New York Stock Exchange",
                ZoneId.of("America/New_York"),
                HorarioOperacion.de(LocalTime.of(9, 30), LocalTime.of(16, 0)), feriados));

        registrar(Mercado.conDiasHabilesEstandar(CodigoMercado.NASDAQ, "NASDAQ",
                ZoneId.of("America/New_York"),
                HorarioOperacion.de(LocalTime.of(9, 30), LocalTime.of(16, 0)), feriados));

        registrar(Mercado.conDiasHabilesEstandar(CodigoMercado.LSE, "London Stock Exchange",
                ZoneId.of("Europe/London"),
                HorarioOperacion.de(LocalTime.of(8, 0), LocalTime.of(16, 30)), feriados));

        registrar(Mercado.conDiasHabilesEstandar(CodigoMercado.TSE, "Tokyo Stock Exchange",
                ZoneId.of("Asia/Tokyo"),
                HorarioOperacion.de(LocalTime.of(9, 0), LocalTime.of(15, 0)), feriados));

        registrar(Mercado.conDiasHabilesEstandar(CodigoMercado.ASX, "Australian Securities Exchange",
                ZoneId.of("Australia/Sydney"),
                HorarioOperacion.de(LocalTime.of(10, 0), LocalTime.of(16, 0)), feriados));
    }

    private void registrar(Mercado mercado) {
        mercados.put(mercado.codigo(), mercado);
    }

    private CalendarioFeriados construirFeriados(MercadosProperties properties) {
        if (properties == null || properties.getFeriados() == null || properties.getFeriados().isEmpty()) {
            return CalendarioFeriados.vacio();
        }
        CalendarioFeriados.Builder builder = CalendarioFeriados.builder();
        for (MercadosProperties.Feriado feriado : properties.getFeriados()) {
            CodigoMercado codigo = CodigoMercado.desde(feriado.getMercado());
            LocalDate fecha = LocalDate.parse(feriado.getFecha());
            String descripcion = feriado.getDescripcion() != null ? feriado.getDescripcion() : "Feriado";
            builder.agregarFeriado(codigo, fecha, descripcion);
        }
        return builder.construir();
    }

    @Override
    public List<Mercado> listar() {
        return new ArrayList<>(mercados.values());
    }

    @Override
    public Optional<Mercado> buscarPorCodigo(CodigoMercado codigo) {
        return Optional.ofNullable(mercados.get(codigo));
    }
}
