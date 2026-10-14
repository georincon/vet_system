package com.veterinaria.inmunizacion.api.dto;

import com.veterinaria.inmunizacion.domain.Antiparasitario;

public record AntiparasitarioResponse(
        Long id,
        String codigo,
        String nombre,
        String tipo,
        String principioActivo,
        Long especieId,
        String especie,
        int intervaloDias,
        Long itemId,
        boolean activo) {

    public static AntiparasitarioResponse from(Antiparasitario a) {
        return new AntiparasitarioResponse(a.getId(), a.getCodigo(), a.getNombre(), a.getTipo(),
                a.getPrincipioActivo(), a.getEspecie() == null ? null : a.getEspecie().getId(),
                a.getEspecie() == null ? null : a.getEspecie().getNombre(), a.getIntervaloDias(), a.getItemId(),
                a.isActivo());
    }
}
