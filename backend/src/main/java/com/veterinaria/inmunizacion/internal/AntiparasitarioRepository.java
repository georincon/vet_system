package com.veterinaria.inmunizacion.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.inmunizacion.domain.Antiparasitario;

public interface AntiparasitarioRepository extends JpaRepository<Antiparasitario, Long> {

    List<Antiparasitario> findAllByOrderByNombreAsc();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
