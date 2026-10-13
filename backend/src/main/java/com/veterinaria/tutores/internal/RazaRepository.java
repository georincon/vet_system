package com.veterinaria.tutores.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.tutores.domain.Raza;

public interface RazaRepository extends JpaRepository<Raza, Long> {

    List<Raza> findByActivoTrueOrderByNombreAsc();

    List<Raza> findByEspecieIdAndActivoTrueOrderByNombreAsc(Long especieId);

    boolean existsByEspecieIdAndNombreIgnoreCase(Long especieId, String nombre);
}
