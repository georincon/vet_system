package com.veterinaria.clinica.api.dto;

import java.time.LocalDate;

import com.veterinaria.clinica.domain.Alergia;

public record AlergiaResponse(Long id, String descripcion, String severidad, String tipo, String reaccion,
        LocalDate registradaEn, boolean activo) {

    public static AlergiaResponse from(Alergia a) {
        return new AlergiaResponse(a.getId(), a.getDescripcion(), a.getSeveridad(), a.getTipo(), a.getReaccion(),
                a.getRegistradaEn(), a.isActivo());
    }
}
