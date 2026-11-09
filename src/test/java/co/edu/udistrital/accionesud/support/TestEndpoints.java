package co.edu.udistrital.accionesud.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TestEndpoints {

    @PostMapping("/test/validacion")
    public Map<String, Object> validacion(@Valid @RequestBody ValidacionRequest request) {
        return Map.of("nombre", request.nombre(), "cantidad", request.cantidad());
    }

    @GetMapping("/test/error")
    public Map<String, Object> error() {
        throw new IllegalStateException("fallo simulado");
    }

    @GetMapping("/test/trace")
    public Map<String, Object> trace() {
        return Map.of("traceId", String.valueOf(MDC.get("traceId")));
    }

    @GetMapping("/admin/ping")
    public Map<String, Object> admin() {
        return Map.of("estado", "ok");
    }

    public record ValidacionRequest(
            @NotBlank String nombre,
            @Min(1) int cantidad) {
    }
}
