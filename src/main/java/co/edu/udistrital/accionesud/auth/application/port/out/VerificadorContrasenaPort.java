package co.edu.udistrital.accionesud.auth.application.port.out;

public interface VerificadorContrasenaPort {

    boolean coincide(String contrasena, String contrasenaHash);
}
