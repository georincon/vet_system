package com.veterinaria.tutores.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.tutores.domain.Especie;

public interface EspecieRepository extends JpaRepository<Especie, Long> {

    List<Especie> findByActivoTrueOrderByNombreAsc();

    Optional<Especie> findByNombre(String nombre);
}
