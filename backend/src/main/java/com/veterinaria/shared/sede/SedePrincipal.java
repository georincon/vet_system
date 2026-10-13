package com.veterinaria.shared.sede;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Resuelve la sede principal (sembrada en V12), asignada por defecto a citas y consultas nuevas. */
@Component
public class SedePrincipal {

    private final JdbcClient jdbc;
    private volatile Long id;

    SedePrincipal(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Long id() {
        Long actual = id;
        if (actual == null) {
            actual = jdbc.sql("select id from sede where es_principal").query(Long.class).single();
            id = actual;
        }
        return actual;
    }
}
