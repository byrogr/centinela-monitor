package com.rmsolutions.centinela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada.
 *
 * Modo BACKEND (por defecto):
 *   java -jar centinela-monitor.jar
 *   -> Levanta el webhook REST, el productor y el consumidor de Kafka.
 *
 * Modo SIMULADOR:
 *   java -jar centinela-monitor.jar --spring.profiles.active=simulator
 *   -> Genera eventos OSD realistas y los envia por HTTP al backend.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CentinelaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CentinelaApplication.class, args);
    }
}
