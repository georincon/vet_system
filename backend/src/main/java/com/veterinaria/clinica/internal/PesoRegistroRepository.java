package com.veterinaria.clinica.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.clinica.domain.PesoRegistro;

public interface PesoRegistroRepository extends JpaRepository<PesoRegistro, Long> {

    List<PesoRegistro> findByPacienteIdOrderByFechaDescIdDesc(Long pacienteId);

    Optional<PesoRegistro> findFirstByPacienteIdOrderByFechaDescIdDesc(Long pacienteId);
}
