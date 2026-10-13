package com.veterinaria.clinica.internal;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.veterinaria.clinica.domain.Consulta;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    long countByCodigoStartingWith(String prefijo);

    boolean existsByCitaId(Long citaId);

    boolean existsByCitaIdAndIdNot(Long citaId, Long id);

    /** Consultas activas, filtradas opcionalmente por fecha, paciente y/o estado (ABIERTA/CERRADA). */
    @EntityGraph(attributePaths = {"paciente", "paciente.especie", "paciente.raza", "veterinario", "cita",
            "cita.tipoCita", "cita.sala"})
    @Query("""
            select c from Consulta c
            where c.activo = true
              and (cast(:fecha as LocalDate) is null or c.fecha = :fecha)
              and (:pacienteId is null or c.paciente.id = :pacienteId)
              and (:estado is null or c.estado = :estado)
            """)
    Page<Consulta> buscar(@Param("fecha") LocalDate fecha, @Param("pacienteId") Long pacienteId,
            @Param("estado") String estado, Pageable pageable);
}
