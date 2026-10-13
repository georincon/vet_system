package com.veterinaria.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Metadatos de la documentacion OpenAPI y esquema de autenticacion Bearer JWT. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI veterinariaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Sistema de Gestion Veterinaria")
                        .description("Backend modular para la operacion clinica de una clinica veterinaria.")
                        .version("0.1.0")
                        .contact(new Contact().name("Equipo Veterinaria")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
