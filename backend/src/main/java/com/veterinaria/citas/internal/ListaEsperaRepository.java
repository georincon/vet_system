package com.veterinaria.citas.internal;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.veterinaria.citas.domain.ListaEspera;

public interface ListaEsperaRepository extends JpaRepository<ListaEspera, Long> {

    @EntityGraph(attributePaths = {"paciente", "paciente.especie", "paciente.raza", "tutor", "tipoCita", "veterinario"})
    @Query("""
            select l from ListaEspera l
            where (:estado is null or l.estado = :estado)
            order by l.prioridad asc, l.creadoEn asc
            """)
    List<ListaEspera> buscar(@Param("estado") String estado);

    @EntityGraph(attributePaths = {"paciente", "paciente.especie", "paciente.raza", "tutor", "tipoCita", "veterinario"})
    java.util.Optional<ListaEspera> findWithDetalleById(Long id);
}
