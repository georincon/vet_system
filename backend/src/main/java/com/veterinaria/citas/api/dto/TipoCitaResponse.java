package com.veterinaria.citas.api.dto;

import com.veterinaria.citas.domain.TipoCita;

public record TipoCitaResponse(Long id, String nombre, int duracionMinutos, String color) {

    public static TipoCitaResponse from(TipoCita t) {
        return t == null ? null : new TipoCitaResponse(t.getId(), t.getNombre(), t.getDuracionMinutos(), t.getColor());
    }
}
