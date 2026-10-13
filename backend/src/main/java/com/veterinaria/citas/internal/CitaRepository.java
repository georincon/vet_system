package com.veterinaria.citas.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.veterinaria.citas.domain.Cita;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    long countByCodigoStartingWith(String prefijo);

    @EntityGraph(attributePaths = {"paciente", "paciente.especie", "paciente.raza", "veterinario", "estadoCita", "tipoCita", "sala"})
    Page<Cita> findByActivoTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"paciente", "paciente.especie", "paciente.raza", "veterinario", "estadoCita", "tipoCita", "sala"})
    Page<Cita> findByActivoTrueAndFecha(LocalDate fecha, Pageable pageable);

    /** Hay una cita activa del veterinario que se traslapa con el rango (excluyendo {@code excluirId}). */
    @Query("""
            select count(c) > 0 from Cita c
            where c.activo = true and c.id <> :excluirId
              and c.veterinario.id = :veterinarioId
              and c.estadoCita.codigo not in :liberan
              and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio
            """)
    boolean existeTraslapeVeterinario(@Param("veterinarioId") Long veterinarioId, @Param("inicio") Instant inicio,
            @Param("fin") Instant fin, @Param("excluirId") Long excluirId, @Param("liberan") Collection<String> liberan);

    /** Hay una cita activa del paciente que se traslapa con el rango (excluyendo {@code excluirId}). */
    @Query("""
            select count(c) > 0 from Cita c
            where c.activo = true and c.id <> :excluirId
              and c.paciente.id = :pacienteId
              and c.estadoCita.codigo not in :liberan
              and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio
            """)
    boolean existeTraslapePaciente(@Param("pacienteId") Long pacienteId, @Param("inicio") Instant inicio,
            @Param("fin") Instant fin, @Param("excluirId") Long excluirId, @Param("liberan") Collection<String> liberan);

    /** Hay una cita activa en la sala que se traslapa con el rango (excluyendo {@code excluirId}). */
    @Query("""
            select count(c) > 0 from Cita c
            where c.activo = true and c.id <> :excluirId
              and c.sala.id = :salaId
              and c.estadoCita.codigo not in :liberan
              and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio
            """)
    boolean existeTraslapeSala(@Param("salaId") Long salaId, @Param("inicio") Instant inicio,
            @Param("fin") Instant fin, @Param("excluirId") Long excluirId, @Param("liberan") Collection<String> liberan);

    /** Cuantas citas vigentes del veterinario y/o la sala caen dentro de un rango (para validar bloqueos). */
    @Query("""
            select count(c) from Cita c
            where c.activo = true
              and c.estadoCita.codigo not in :liberan
              and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio
              and ((:vetId is not null and c.veterinario.id = :vetId)
                or (:salaId is not null and c.sala.id = :salaId))
            """)
    long contarVigentesEnRango(@Param("vetId") Long veterinarioId, @Param("salaId") Long salaId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin, @Param("liberan") Collection<String> liberan);

    /** Citas del calendario: traslapadas con [desde, hasta), filtradas por veterinario y/o sala. */
    @EntityGraph(attributePaths = {"paciente", "paciente.especie", "paciente.raza", "veterinario", "estadoCita",
            "tipoCita", "sala"})
    @Query("""
            select c from Cita c
            where c.activo = true
              and c.fechaHoraInicio < :hasta and c.fechaHoraFin > :desde
              and (:vetId is null or c.veterinario.id = :vetId)
              and (:salaId is null or c.sala.id = :salaId)
              and (:incluirLiberadas = true or c.estadoCita.codigo not in :liberan)
            order by c.fechaHoraInicio, c.id
            """)
    List<Cita> agenda(@Param("desde") Instant desde, @Param("hasta") Instant hasta, @Param("vetId") Long veterinarioId,
            @Param("salaId") Long salaId, @Param("incluirLiberadas") boolean incluirLiberadas,
            @Param("liberan") Collection<String> liberan);
}
