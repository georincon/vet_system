package com.veterinaria.tutores.api.dto;

import com.veterinaria.tutores.domain.Especie;

public record EspecieResponse(Long id, String nombre, boolean activo) {

    public static EspecieResponse from(Especie e) {
        return new EspecieResponse(e.getId(), e.getNombre(), e.isActivo());
    }
}
