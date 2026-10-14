package com.veterinaria.inmunizacion.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.inmunizacion.domain.Vacuna;

public interface VacunaRepository extends JpaRepository<Vacuna, Long> {

    List<Vacuna> findAllByOrderByNombreAsc();

    Optional<Vacuna> findByCodigo(String codigo);

    Optional<Vacuna> findFirstByNombreIgnoreCase(String nombre);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
