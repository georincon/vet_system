package com.veterinaria.clinica.internal;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.clinica.domain.ConsultaAddendum;

public interface ConsultaAddendumRepository extends JpaRepository<ConsultaAddendum, Long> {

    @EntityGraph(attributePaths = "autor")
    List<ConsultaAddendum> findByConsultaIdOrderByCreadoEnAscIdAsc(Long consultaId);
}
