package com.veterinaria.inmunizacion.api.dto;

import java.util.List;

import com.veterinaria.inmunizacion.domain.EsquemaVacunal;

public record EsquemaResponse(
        Long id,
        String codigo,
        String nombre,
        Long vacunaId,
        String vacuna,
        Long especieId,
        String especie,
        String fuente,
        String notas,
        boolean activo,
        List<Dosis> dosis) {

    public record Dosis(Long id, int orden, String nombreDosis, Integer edadMinimaSemanas, Integer intervaloDiasPrevia,
            boolean esRefuerzo, Integer repetirCadaDias) {
    }

    public static EsquemaResponse from(EsquemaVacunal e) {
        return new EsquemaResponse(e.getId(), e.getCodigo(), e.getNombre(), e.getVacuna().getId(),
                e.getVacuna().getNombre(), e.getEspecie() == null ? null : e.getEspecie().getId(),
                e.getEspecie() == null ? null : e.getEspecie().getNombre(), e.getFuente(), e.getNotas(), e.isActivo(),
                e.getDosis().stream().map(d -> new Dosis(d.getId(), d.getOrden(), d.getNombreDosis(),
                        d.getEdadMinimaSemanas(), d.getIntervaloDiasPrevia(), d.isEsRefuerzo(),
                        d.getRepetirCadaDias())).toList());
    }
}
