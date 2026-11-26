package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifica el encabezado {@code Stripe-Signature} de un webhook.
 *
 * <p>Formato: {@code t=<segundos>,v1=<hmac>[,v1=<hmac>...]}. La firma es HMAC-SHA256, con el
 * secreto del endpoint ({@code whsec_...}), de {@code "<t>." + cuerpo} tal cual llegó (bytes sin
 * reinterpretar). Se rechaza un aviso más viejo que la tolerancia para que no se pueda reenviar uno
 * capturado. La comparación es de tiempo constante.</p>
 */
public class StripeFirmaVerificador {

    private final byte[] secreto;
    private final Clock reloj;
    private final Duration tolerancia;

    public StripeFirmaVerificador(String secreto, Clock reloj, Duration tolerancia) {
        Objects.requireNonNull(secreto);
        if (secreto.isBlank()) {
            throw new IllegalStateException("Falta el secreto del webhook de Stripe (STRIPE_WEBHOOK_SECRET)");
        }
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
        this.reloj = Objects.requireNonNull(reloj);
        this.tolerancia = Objects.requireNonNull(tolerancia);
    }

    /** {@code true} si la firma es válida para ese cuerpo y está dentro de la tolerancia. */
    public boolean esValida(byte[] cuerpo, String encabezado) {
        if (cuerpo == null || encabezado == null || encabezado.isBlank()) {
            return false;
        }
        Long marca = null;
        List<String> firmas = new ArrayList<>();
        for (String parte : encabezado.split(",")) {
            String[] clave = parte.trim().split("=", 2);
            if (clave.length != 2) {
                continue;
            }
            if ("t".equals(clave[0])) {
                try {
                    marca = Long.parseLong(clave[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if ("v1".equals(clave[0])) {
                firmas.add(clave[1]);
            }
        }
        if (marca == null || firmas.isEmpty()) {
            return false;
        }
        long ahora = reloj.instant().getEpochSecond();
        if (Math.abs(ahora - marca) > tolerancia.toSeconds()) {
            return false;
        }
        byte[] esperada = HexFormat.of().formatHex(firmar(marca, cuerpo)).getBytes(StandardCharsets.US_ASCII);
        for (String firma : firmas) {
            if (MessageDigest.isEqual(esperada, firma.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    /** Firma un cuerpo como lo haría Stripe (útil en pruebas y con el Stripe CLI). */
    public String encabezado(long marca, byte[] cuerpo) {
        return "t=" + marca + ",v1=" + HexFormat.of().formatHex(firmar(marca, cuerpo));
    }

    private byte[] firmar(long marca, byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            mac.update((marca + ".").getBytes(StandardCharsets.US_ASCII));
            return mac.doFinal(cuerpo);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC-SHA256", e);
        }
    }
}
