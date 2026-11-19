package co.edu.udistrital.accionesud.auth.application.port.out;

public interface HasheadorPort {

    String hash(String valor);

    boolean coincide(String valor, String hash);
}
