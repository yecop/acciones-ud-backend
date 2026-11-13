package co.edu.udistrital.accionesud.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "accionesud.auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();
    private Mfa mfa = new Mfa();
    private Correo correo = new Correo();
    private Login login = new Login();
    private Demo demo = new Demo();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Mfa getMfa() {
        return mfa;
    }

    public void setMfa(Mfa mfa) {
        this.mfa = mfa;
    }

    public Correo getCorreo() {
        return correo;
    }

    public void setCorreo(Correo correo) {
        this.correo = correo;
    }

    public Login getLogin() {
        return login;
    }

    public void setLogin(Login login) {
        this.login = login;
    }

    public Demo getDemo() {
        return demo;
    }

    public void setDemo(Demo demo) {
        this.demo = demo;
    }

    public static class Jwt {
        private String secreto = "";
        private String emisor = "acciones-ud";
        private long accessTtlSegundos = 900;
        private long refreshTtlSegundos = 604800;

        public String getSecreto() {
            return secreto;
        }

        public void setSecreto(String secreto) {
            this.secreto = secreto;
        }

        public String getEmisor() {
            return emisor;
        }

        public void setEmisor(String emisor) {
            this.emisor = emisor;
        }

        public long getAccessTtlSegundos() {
            return accessTtlSegundos;
        }

        public void setAccessTtlSegundos(long accessTtlSegundos) {
            this.accessTtlSegundos = accessTtlSegundos;
        }

        public long getRefreshTtlSegundos() {
            return refreshTtlSegundos;
        }

        public void setRefreshTtlSegundos(long refreshTtlSegundos) {
            this.refreshTtlSegundos = refreshTtlSegundos;
        }
    }

    public static class Mfa {
        private long expiracionSegundos = 300;
        private int maxIntentos = 5;
        private long bloqueoSegundos = 900;
        private long reenvioEsperaSegundos = 60;
        private int maxReenvios = 3;
        private int longitudCodigo = 6;

        public long getExpiracionSegundos() {
            return expiracionSegundos;
        }

        public void setExpiracionSegundos(long expiracionSegundos) {
            this.expiracionSegundos = expiracionSegundos;
        }

        public int getMaxIntentos() {
            return maxIntentos;
        }

        public void setMaxIntentos(int maxIntentos) {
            this.maxIntentos = maxIntentos;
        }

        public long getBloqueoSegundos() {
            return bloqueoSegundos;
        }

        public void setBloqueoSegundos(long bloqueoSegundos) {
            this.bloqueoSegundos = bloqueoSegundos;
        }

        public long getReenvioEsperaSegundos() {
            return reenvioEsperaSegundos;
        }

        public void setReenvioEsperaSegundos(long reenvioEsperaSegundos) {
            this.reenvioEsperaSegundos = reenvioEsperaSegundos;
        }

        public int getMaxReenvios() {
            return maxReenvios;
        }

        public void setMaxReenvios(int maxReenvios) {
            this.maxReenvios = maxReenvios;
        }

        public int getLongitudCodigo() {
            return longitudCodigo;
        }

        public void setLongitudCodigo(int longitudCodigo) {
            this.longitudCodigo = longitudCodigo;
        }
    }

    public static class Correo {
        private String remitente = "no-reply@accionesud.local";
        private String asunto = "Código de verificación - Acciones UD";

        public String getRemitente() {
            return remitente;
        }

        public void setRemitente(String remitente) {
            this.remitente = remitente;
        }

        public String getAsunto() {
            return asunto;
        }

        public void setAsunto(String asunto) {
            this.asunto = asunto;
        }
    }

    public static class Login {
        private int maxFallos = 5;
        private long ventanaSegundos = 900;

        public int getMaxFallos() {
            return maxFallos;
        }

        public void setMaxFallos(int maxFallos) {
            this.maxFallos = maxFallos;
        }

        public long getVentanaSegundos() {
            return ventanaSegundos;
        }

        public void setVentanaSegundos(long ventanaSegundos) {
            this.ventanaSegundos = ventanaSegundos;
        }
    }

    public static class Demo {
        private String correo = "inversionista@accionesud.local";
        private String clave = "Clave123!";

        public String getCorreo() {
            return correo;
        }

        public void setCorreo(String correo) {
            this.correo = correo;
        }

        public String getClave() {
            return clave;
        }

        public void setClave(String clave) {
            this.clave = clave;
        }
    }
}
