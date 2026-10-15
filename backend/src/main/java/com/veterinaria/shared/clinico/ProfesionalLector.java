package com.veterinaria.shared.clinico;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Resuelve nombre y numero profesional (tabla veterinario) de un usuario, para firmas de documentos. */
@Component
public class ProfesionalLector {

    private final JdbcClient jdbc;

    public ProfesionalLector(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProfesionalBasico> buscar(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        return jdbc.sql("""
                select u.id, u.nombre, v.numero_profesional, v.especialidad
                  from usuario u left join veterinario v on v.usuario_id = u.id where u.id = :id
                """).param("id", usuarioId)
                .query((rs, n) -> new ProfesionalBasico(rs.getLong("id"), rs.getString("nombre"),
                        rs.getString("numero_profesional"), rs.getString("especialidad")))
                .optional();
    }
}
