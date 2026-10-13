package com.veterinaria;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para pruebas de integracion: levanta un PostgreSQL 18 real en un
 * contenedor via Testcontainers y conecta la aplicacion con @ServiceConnection,
 * de modo que Flyway valida las migraciones contra la base real.
 *
 * <p>{@code @DirtiesContext} por clase: como cada clase gestiona su propio
 * contenedor (ciclo de vida de JUnit), se evita que el contexto cacheado de
 * Spring apunte a un contenedor ya reciclado entre clases de prueba.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class BaseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("veterinaria_test")
            .withUsername("veterinaria_app")
            .withPassword("veterinaria_dev_pwd");
}