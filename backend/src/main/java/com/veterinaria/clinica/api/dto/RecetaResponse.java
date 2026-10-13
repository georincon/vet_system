package com.veterinaria.clinica.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.veterinaria.clinica.domain.Receta;

public record RecetaResponse(
        Long id,
        LocalDate fecha,
        LocalDate validaHasta,
        String estado,
        boolean esControlada,
        String indicacionesGenerales,
        List<RecetaItemDto> items) {

    public static RecetaResponse from(Receta r) {
        return new RecetaResponse(r.getId(), r.getFecha(), r.getValidaHasta(), r.getEstado(), r.isEsControlada(),
                r.getIndicacionesGenerales(), r.getItems().stream().map(RecetaItemDto::from).toList());
    }
}
