package com.veterinaria.inmunizacion.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.inmunizacion.domain.EsquemaVacunal;

public interface EsquemaVacunalRepository extends JpaRepository<EsquemaVacunal, Long> {

    List<EsquemaVacunal> findAllByOrderByNombreAsc();

    List<EsquemaVacunal> findByVacunaIdAndActivoTrue(Long vacunaId);

    List<EsquemaVacunal> findByActivoTrueOrderByNombreAsc();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
