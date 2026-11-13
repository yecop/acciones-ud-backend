package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.security;

import co.edu.udistrital.accionesud.auth.application.port.out.TokenJwtPort;
import co.edu.udistrital.accionesud.auth.domain.model.Usuario;
import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class TokenJwtAdapter implements TokenJwtPort {

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public TokenJwtAdapter(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public String emitirAccessToken(Usuario usuario, Instant ahora) {
        Instant expiraEn = ahora.plusSeconds(properties.getJwt().getAccessTtlSegundos());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getEmisor())
                .subject(usuario.id().toString())
                .issuedAt(ahora)
                .expiresAt(expiraEn)
                .id(UUID.randomUUID().toString())
                .claim("rol", usuario.rol())
                .claim("correo", usuario.correo())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public long expiracionSegundos() {
        return properties.getJwt().getAccessTtlSegundos();
    }
}
