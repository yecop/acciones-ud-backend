package co.edu.udistrital.accionesud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AccionesUdBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccionesUdBackendApplication.class, args);
    }
}
