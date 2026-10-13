package com.veterinaria.clinica.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.veterinaria.clinica.domain.SignoVital;

public record SignoVitalResponse(
        Long id,
        BigDecimal temperatura,
        Integer frecuenciaCardiaca,
        Integer frecuenciaRespiratoria,
        BigDecimal pesoKg,
        String condicionCorporal,
        Integer puntajeCondicionCorporal,
        String mucosas,
        BigDecimal tiempoLlenadoCapilar,
        Integer escalaDolor,
        BigDecimal hidratacionPct,
        Integer presionSistolica,
        BigDecimal spo2,
        BigDecimal glucosaMgDl,
        String notas,
        Instant registradoEn,
        Long registradoPor) {

    public static SignoVitalResponse from(SignoVital s) {
        return new SignoVitalResponse(s.getId(), s.getTemperatura(), s.getFrecuenciaCardiaca(),
                s.getFrecuenciaRespiratoria(), s.getPesoKg(), s.getCondicionCorporal(),
                s.getPuntajeCondicionCorporal() == null ? null : s.getPuntajeCondicionCorporal().intValue(),
                s.getMucosas(), s.getTiempoLlenadoCapilar(),
                s.getEscalaDolor() == null ? null : s.getEscalaDolor().intValue(), s.getHidratacionPct(),
                s.getPresionSistolica(), s.getSpo2(), s.getGlucosaMgDl(), s.getNotas(), s.getRegistradoEn(),
                s.getRegistradoPor());
    }
}
