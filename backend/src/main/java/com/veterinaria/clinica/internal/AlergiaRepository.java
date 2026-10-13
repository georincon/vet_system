package com.veterinaria.clinica.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.clinica.domain.Alergia;

public interface AlergiaRepository extends JpaRepository<Alergia, Long> {

    List<Alergia> findByPacienteIdAndActivoTrueOrderByIdAsc(Long pacienteId);

    List<Alergia> findByPacienteIdOrderByIdAsc(Long pacienteId);
}
