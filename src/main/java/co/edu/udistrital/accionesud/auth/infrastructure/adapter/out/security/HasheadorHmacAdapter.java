package co.edu.udistrital.accionesud.auth.infrastructure.adapter.out.security;

import co.edu.udistrital.accionesud.auth.application.port.out.HasheadorPort;
import co.edu.udistrital.accionesud.auth.infrastructure.config.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class HasheadorHmacAdapter implements HasheadorPort {

    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec clave;

    public HasheadorHmacAdapter(AuthProperties properties) {
        this.clave = new SecretKeySpec(
                properties.getJwt().getSecreto().getBytes(StandardCharsets.UTF_8), ALGORITMO);
    }

    @Override
    public String hash(String valor) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(clave);
            return HexFormat.of().formatHex(
                    mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible hashear el valor", ex);
        }
    }

    @Override
    public boolean coincide(String valor, String hash) {
        byte[] esperado = hash.getBytes(StandardCharsets.UTF_8);
        byte[] calculado = hash(valor).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, calculado);
    }
}
