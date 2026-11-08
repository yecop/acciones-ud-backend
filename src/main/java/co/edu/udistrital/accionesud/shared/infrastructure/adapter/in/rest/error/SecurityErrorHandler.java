package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ErrorResponseFactory factory;
    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ErrorResponseFactory factory, ObjectMapper objectMapper) {
        this.factory = factory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        escribir(request, response, CodigoError.NO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escribir(request, response, CodigoError.ACCESO_DENEGADO);
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response,
                          CodigoError codigo) throws IOException {
        ErrorResponse cuerpo = factory.crear(request, codigo);
        response.setStatus(codigo.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (codigo == CodigoError.NO_AUTENTICADO) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"acciones-ud\"");
        }
        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
