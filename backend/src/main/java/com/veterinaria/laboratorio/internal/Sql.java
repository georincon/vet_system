package com.veterinaria.laboratorio.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.veterinaria.laboratorio.api.dto.LabDtos.Persona;

/** Lectores de columnas anulables para los RowMapper del modulo. */
public final class Sql {

    private Sql() {
    }

    public static Instant instante(ResultSet rs, String col) throws SQLException {
        OffsetDateTime t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    public static Long largo(ResultSet rs, String col) throws SQLException {
        return (Long) rs.getObject(col);
    }

    public static Integer entero(ResultSet rs, String col) throws SQLException {
        return (Integer) rs.getObject(col);
    }

    public static LocalDate fecha(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDate.class);
    }

    public static Persona persona(ResultSet rs, String colId, String colNombre) throws SQLException {
        Long id = largo(rs, colId);
        return id == null ? null : new Persona(id, rs.getString(colNombre));
    }
}
