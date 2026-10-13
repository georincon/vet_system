package com.veterinaria.tutores.api.dto;

import com.veterinaria.tutores.domain.Raza;

public record RazaResponse(
        Long id,
        Long especieId,
        String nombre,
        boolean activo,
        Integer grupoFci,
        Integer numeroFci,
        String origen,
        String tamano,
        String reconocimientoFci,
        boolean potencialmentePeligrosa) {

    public static RazaResponse from(Raza r) {
        return new RazaResponse(r.getId(), r.getEspecie().getId(), r.getNombre(), r.isActivo(),
                r.getGrupoFci() == null ? null : r.getGrupoFci().intValue(), r.getNumeroFci(), r.getOrigen(),
                r.getTamano(), r.getReconocimientoFci(), r.isPotencialmentePeligrosa());
    }
}
