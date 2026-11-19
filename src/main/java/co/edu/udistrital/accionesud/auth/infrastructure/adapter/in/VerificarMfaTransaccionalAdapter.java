package co.edu.udistrital.accionesud.auth.infrastructure.adapter.in;

import co.edu.udistrital.accionesud.auth.application.dto.ContextoIntento;
import co.edu.udistrital.accionesud.auth.application.dto.ResultadoTokens;
import co.edu.udistrital.accionesud.auth.application.port.in.VerificarMfaUseCase;
import co.edu.udistrital.accionesud.auth.application.service.ServicioAutenticacion;
import co.edu.udistrital.accionesud.auth.domain.exception.AutenticacionException;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
@Profile("!test")
public class VerificarMfaTransaccionalAdapter implements VerificarMfaUseCase {

    private final ServicioAutenticacion servicio;

    public VerificarMfaTransaccionalAdapter(ServicioAutenticacion servicio) {
        this.servicio = servicio;
    }

    @Override
    @Transactional(noRollbackFor = AutenticacionException.class)
    public ResultadoTokens verificarMfa(String mfaToken, String codigo, ContextoIntento contexto) {
        return servicio.verificarMfa(mfaToken, codigo, contexto);
    }
}
