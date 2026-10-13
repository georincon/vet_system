package com.veterinaria.citas.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.citas.domain.TipoCita;

public interface TipoCitaRepository extends JpaRepository<TipoCita, Long> {

    List<TipoCita> findByActivoTrueOrderByNombreAsc();
}
