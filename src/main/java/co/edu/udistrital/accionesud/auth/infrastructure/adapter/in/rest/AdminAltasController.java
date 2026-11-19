package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.auth.application.port.in.ReintentarAltasPendientesUseCase;
import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Profile("!test")
@RestController
@RequestMapping("/api/v1/admin/altas")
public class AdminAltasController {

    private final ReintentarAltasPendientesUseCase reintentar;
    private final AuthProperties properties;

    public AdminAltasController(ReintentarAltasPendientesUseCase reintentar, AuthProperties properties) {
        this.reintentar = reintentar;
        this.properties = properties;
    }

    @PostMapping("/reintentar")
    public Map<String, Integer> reintentar() {
        return Map.of("completadas", reintentar.reintentar(properties.reintento().maximoPorCiclo()));
    }
}