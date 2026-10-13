package com.veterinaria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del sistema de gestion veterinaria.
 *
 * <p>La aplicacion es un monolito modular (Spring Modulith): cada subpaquete
 * directo de {@code com.veterinaria} es un modulo con frontera verificada por
 * el test {@link com.veterinaria.ModularityTests}.
 */
@SpringBootApplication
public class VeterinariaApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeterinariaApplication.class, args);
    }
}
