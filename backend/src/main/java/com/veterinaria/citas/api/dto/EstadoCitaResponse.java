package com.veterinaria.citas.api.dto;

import com.veterinaria.citas.domain.EstadoCita;

public record EstadoCitaResponse(Integer id, String codigo, String nombre, boolean activo) {

    public static EstadoCitaResponse from(EstadoCita e) {
        return new EstadoCitaResponse(e.getId(), e.getCodigo(), e.getNombre(), e.isActivo());
    }
}
