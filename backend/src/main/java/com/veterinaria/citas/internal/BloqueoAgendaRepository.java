package com.veterinaria.citas.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.veterinaria.citas.domain.BloqueoAgenda;

public interface BloqueoAgendaRepository extends JpaRepository<BloqueoAgenda, Long> {

    /** Bloqueos que se traslapan con el rango y afectan al veterinario o a la sala indicados. */
    @EntityGraph(attributePaths = {"veterinario", "sala"})
    @Query("""
            select b from BloqueoAgenda b
            where b.inicio < :fin and b.fin > :inicio
              and ((:vetId is not null and b.veterinario.id = :vetId)
                or (:salaId is not null and b.sala.id = :salaId))
            order by b.inicio
            """)
    List<BloqueoAgenda> solapados(@Param("vetId") Long veterinarioId, @Param("salaId") Long salaId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin);

    /** Bloqueos del rango para el calendario; sin filtros devuelve todos, con filtros los de cualquiera de ellos. */
    @EntityGraph(attributePaths = {"veterinario", "sala"})
    @Query("""
            select b from BloqueoAgenda b
            where b.inicio < :fin and b.fin > :inicio
              and ((:vetId is null and :salaId is null)
                or (:vetId is not null and b.veterinario.id = :vetId)
                or (:salaId is not null and b.sala.id = :salaId))
            order by b.inicio
            """)
    List<BloqueoAgenda> delRango(@Param("vetId") Long veterinarioId, @Param("salaId") Long salaId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin);
}
