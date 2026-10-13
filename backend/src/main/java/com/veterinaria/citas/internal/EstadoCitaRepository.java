package com.veterinaria.citas.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.citas.domain.EstadoCita;

public interface EstadoCitaRepository extends JpaRepository<EstadoCita, Integer> {

    Optional<EstadoCita> findByCodigo(String codigo);

    List<EstadoCita> findByActivoTrueOrderByIdAsc();
}
