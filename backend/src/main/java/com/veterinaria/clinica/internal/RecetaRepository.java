package com.veterinaria.clinica.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.clinica.domain.Receta;

public interface RecetaRepository extends JpaRepository<Receta, Long> {
}
