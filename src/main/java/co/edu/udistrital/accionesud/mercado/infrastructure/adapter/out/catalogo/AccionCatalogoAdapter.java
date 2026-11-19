package co.edu.udistrital.accionesud.mercado.infrastructure.adapter.out.catalogo;

import co.edu.udistrital.accionesud.mercado.domain.model.Accion;
import co.edu.udistrital.accionesud.mercado.domain.model.CodigoMercado;
import co.edu.udistrital.accionesud.mercado.domain.model.Sector;
import co.edu.udistrital.accionesud.mercado.domain.port.out.AccionCatalogoPort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de acciones en código, idéntico a la semilla de la migración
 * {@code V7__seed_mercados_acciones.sql}.
 *
 * <p>Igual que el catálogo de mercados (SCRUM-23), es temporal: cuando el módulo de
 * persistencia exponga la tabla {@code accion}, basta con otro adaptador de
 * {@link AccionCatalogoPort} sin tocar el dominio ni los casos de uso.</p>
 */
@Component
public class AccionCatalogoAdapter implements AccionCatalogoPort {

    private final Map<String, Accion> acciones = new LinkedHashMap<>();

    public AccionCatalogoAdapter() {
        // EE. UU. (10 tickers, Alpaca feed IEX)
        registrar("AAPL", "Apple Inc.", Sector.TECNOLOGIA, CodigoMercado.NASDAQ, "USD");
        registrar("MSFT", "Microsoft Corporation", Sector.TECNOLOGIA, CodigoMercado.NASDAQ, "USD");
        registrar("JNJ", "Johnson & Johnson", Sector.SALUD, CodigoMercado.NYSE, "USD");
        registrar("JPM", "JPMorgan Chase & Co.", Sector.FINANZAS, CodigoMercado.NYSE, "USD");
        registrar("XOM", "Exxon Mobil Corp.", Sector.ENERGIA, CodigoMercado.NYSE, "USD");
        registrar("GOOGL", "Alphabet Inc.", Sector.TECNOLOGIA, CodigoMercado.NASDAQ, "USD");
        registrar("AMZN", "Amazon.com Inc.", Sector.CONSUMO, CodigoMercado.NASDAQ, "USD");
        registrar("META", "Meta Platforms Inc.", Sector.TECNOLOGIA, CodigoMercado.NASDAQ, "USD");
        registrar("TSLA", "Tesla Inc.", Sector.CONSUMO, CodigoMercado.NASDAQ, "USD");
        registrar("NVDA", "NVIDIA Corp.", Sector.TECNOLOGIA, CodigoMercado.NASDAQ, "USD");
        // Londres
        registrar("HSBA", "HSBC Holdings plc", Sector.FINANZAS, CodigoMercado.LSE, "GBP");
        registrar("BP", "BP plc", Sector.ENERGIA, CodigoMercado.LSE, "GBP");
        registrar("GSK", "GlaxoSmithKline plc", Sector.SALUD, CodigoMercado.LSE, "GBP");
        registrar("ULVR", "Unilever plc", Sector.CONSUMO, CodigoMercado.LSE, "GBP");
        registrar("BARC", "Barclays plc", Sector.FINANZAS, CodigoMercado.LSE, "GBP");
        // Tokio
        registrar("7203", "Toyota Motor Corp.", Sector.CONSUMO, CodigoMercado.TSE, "JPY");
        registrar("6758", "Sony Group Corp.", Sector.TECNOLOGIA, CodigoMercado.TSE, "JPY");
        registrar("9984", "SoftBank Group Corp.", Sector.TECNOLOGIA, CodigoMercado.TSE, "JPY");
        registrar("8306", "Mitsubishi UFJ Financial Group Inc.", Sector.FINANZAS, CodigoMercado.TSE, "JPY");
        registrar("6861", "Keyence Corp.", Sector.TECNOLOGIA, CodigoMercado.TSE, "JPY");
        // Sídney
        registrar("BHP", "BHP Group Ltd.", Sector.MATERIALES, CodigoMercado.ASX, "AUD");
        registrar("CBA", "Commonwealth Bank of Australia", Sector.FINANZAS, CodigoMercado.ASX, "AUD");
        registrar("CSL", "CSL Ltd.", Sector.SALUD, CodigoMercado.ASX, "AUD");
        registrar("WES", "Wesfarmers Ltd.", Sector.CONSUMO, CodigoMercado.ASX, "AUD");
        registrar("WOW", "Woolworths Group Ltd.", Sector.CONSUMO, CodigoMercado.ASX, "AUD");
    }

    private void registrar(String ticker, String nombre, Sector sector, CodigoMercado mercado, String moneda) {
        acciones.put(ticker, new Accion(ticker, nombre, mercado, sector, moneda));
    }

    @Override
    public List<Accion> listar() {
        return List.copyOf(acciones.values());
    }

    @Override
    public Optional<Accion> buscarPorTicker(String ticker) {
        if (ticker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(acciones.get(ticker.trim().toUpperCase(Locale.ROOT)));
    }
}
