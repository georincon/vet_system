package com.veterinaria.citas.api.dto;

import com.veterinaria.citas.domain.Sala;

public record SalaResponse(Long id, String nombre, String tipo, int capacidad, boolean activo) {

    public static SalaResponse from(Sala s) {
        return s == null ? null : new SalaResponse(s.getId(), s.getNombre(), s.getTipo(), s.getCapacidad(), s.isActivo());
    }
}
