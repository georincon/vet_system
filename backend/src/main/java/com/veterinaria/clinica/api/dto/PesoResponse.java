package com.veterinaria.clinica.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.veterinaria.clinica.domain.PesoRegistro;

public record PesoResponse(Long id, BigDecimal pesoKg, LocalDate fecha, Long registradoPor) {

    public static PesoResponse from(PesoRegistro p) {
        return new PesoResponse(p.getId(), p.getPesoKg(), p.getFecha(), p.getRegistradoPor());
    }
}
