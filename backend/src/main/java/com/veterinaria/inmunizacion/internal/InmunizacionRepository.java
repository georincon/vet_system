package com.veterinaria.inmunizacion.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.inmunizacion.domain.Inmunizacion;

public interface InmunizacionRepository extends JpaRepository<Inmunizacion, Long> {

    List<Inmunizacion> findByPacienteIdOrderByFechaAplicacionDescIdDesc(Long pacienteId);

    List<Inmunizacion> findByPacienteIdAndVacunaIdAndEstadoOrderByFechaAplicacionDescIdDesc(
            Long pacienteId, Long vacunaId, String estado);

    long countByPacienteIdAndVacunaIdAndEstado(Long pacienteId, Long vacunaId, String estado);

    boolean existsByConsultaVacunaId(Long consultaVacunaId);
}
