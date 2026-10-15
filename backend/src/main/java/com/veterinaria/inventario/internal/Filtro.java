package com.veterinaria.inventario.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/** Constructor minimo de clausulas WHERE con parametros con nombre (todo valor va como parametro, nunca concatenado). */
public final class Filtro {

    private final StringBuilder where = new StringBuilder();
    private final Map<String, Object> params = new LinkedHashMap<>();

    public Filtro y(String condicion) {
        where.append(where.isEmpty() ? " where " : " and ").append(condicion);
        return this;
    }

    public Filtro y(String condicion, String nombre, Object valor) {
        params.put(nombre, valor);
        return y(condicion);
    }

    /** Agrega la condicion solo si {@code valor} no es nulo. */
    public Filtro si(Object valor, String condicion, String nombre) {
        return valor == null ? this : y(condicion, nombre, valor);
    }

    public Filtro param(String nombre, Object valor) {
        params.put(nombre, valor);
        return this;
    }

    public String sql() {
        return where.toString();
    }

    public Map<String, Object> params() {
        return params;
    }
}
