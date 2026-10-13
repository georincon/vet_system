package com.veterinaria.tutores.internal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.tutores.domain.Paciente;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    long countByCodigoStartingWith(String prefijo);

    boolean existsByMicrochip(String microchip);

    boolean existsByMicrochipAndIdNot(String microchip, Long id);

    @EntityGraph(attributePaths = {"especie", "raza"})
    Page<Paciente> findByNombreContainingIgnoreCaseOrCodigoContainingIgnoreCase(
            String nombre, String codigo, Pageable pageable);

    @EntityGraph(attributePaths = {"especie", "raza"})
    List<Paciente> findDistinctByTutoresTutorIdOrderByNombreAsc(Long tutorId);
}
