package com.veterinaria.shared.clinico;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.veterinaria.shared.error.ResourceNotFoundException;

/** Lee paciente + tutor principal por SQL (solo lectura). */
@Component
public class PacienteLector {

    private static final String SQL = """
            select p.id, p.codigo, p.nombre, p.especie_id, e.nombre as especie, r.nombre as raza, p.sexo,
                   p.fecha_nacimiento, p.microchip,
                   t.id as tutor_id,
                   trim(t.nombres || ' ' || coalesce(t.apellidos, '')) as tutor_nombre,
                   t.tipo_documento || ' ' || t.numero_documento as tutor_documento,
                   t.telefono as tutor_telefono, t.email as tutor_email
              from paciente p
              join especie e on e.id = p.especie_id
              left join raza r on r.id = p.raza_id
              left join lateral (
                    select t2.* from paciente_tutor pt join tutor t2 on t2.id = pt.tutor_id
                     where pt.paciente_id = p.id order by pt.es_principal desc, t2.id limit 1) t on true
             where p.id = :id
            """;

    private final JdbcClient jdbc;

    public PacienteLector(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PacienteBasico> buscar(Long id) {
        return jdbc.sql(SQL).param("id", id).query((rs, n) -> new PacienteBasico(
                rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getLong("especie_id"),
                rs.getString("especie"), rs.getString("raza"), rs.getString("sexo"),
                rs.getObject("fecha_nacimiento", java.time.LocalDate.class), rs.getString("microchip"),
                (Long) rs.getObject("tutor_id"), rs.getString("tutor_nombre"), rs.getString("tutor_documento"),
                rs.getString("tutor_telefono"), rs.getString("tutor_email"))).optional();
    }

    /** @throws ResourceNotFoundException (404) si el paciente no existe */
    public PacienteBasico obtener(Long id) {
        return buscar(id).orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
    }
}
