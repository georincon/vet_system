package com.veterinaria.inmunizacion.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.inmunizacion.domain.Desparasitacion;

public interface DesparasitacionRepository extends JpaRepository<Desparasitacion, Long> {

    List<Desparasitacion> findByPacienteIdOrderByFechaAplicacionDescIdDesc(Long pacienteId);

    List<Desparasitacion> findByPacienteIdAndAntiparasitarioIdAndEstadoOrderByFechaAplicacionDescIdDesc(
            Long pacienteId, Long antiparasitarioId, String estado);
}
