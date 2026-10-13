package com.veterinaria.clinica.internal;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.veterinaria.clinica.domain.PlantillaSoap;

public interface PlantillaSoapRepository extends JpaRepository<PlantillaSoap, Long> {

    /** Plantillas activas generales (sin especie) y, si se indica, las de esa especie. */
    @EntityGraph(attributePaths = "especie")
    @Query("""
            select p from PlantillaSoap p
            where p.activo = true
              and (:especieId is null or p.especie is null or p.especie.id = :especieId)
            order by p.nombre
            """)
    List<PlantillaSoap> buscar(@Param("especieId") Long especieId);
}
