package com.veterinaria;

import org.junit.jupiter.api.Test;

/**
 * Prueba que las migraciones Flyway del esquema del MVP se aplican sin errores
 * sobre un PostgreSQL 18 real y que el contexto de la aplicacion arranca.
 */
class SchemaMigrationsTest extends BaseIntegrationTest {

    @Test
    void contextLoads() {
        // Con arrancar el contexto (Flyway + JPA validate + beans) la prueba pasa.
    }
}