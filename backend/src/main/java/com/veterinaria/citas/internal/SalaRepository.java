package com.veterinaria.citas.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.citas.domain.Sala;

public interface SalaRepository extends JpaRepository<Sala, Long> {

    List<Sala> findByActivoTrueOrderByNombreAsc();

    List<Sala> findAllByOrderByNombreAsc();

    boolean existsBySedeIdAndNombreIgnoreCase(Long sedeId, String nombre);

    boolean existsBySedeIdAndNombreIgnoreCaseAndIdNot(Long sedeId, String nombre, Long id);
}
