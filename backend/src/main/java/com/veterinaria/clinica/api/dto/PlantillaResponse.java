package com.veterinaria.clinica.api.dto;

import com.veterinaria.clinica.domain.PlantillaSoap;

public record PlantillaResponse(Long id, String nombre, Long especieId, String tipoConsulta, String subjetivo,
        String objetivo, String evaluacion, String plan) {

    public static PlantillaResponse from(PlantillaSoap p) {
        return new PlantillaResponse(p.getId(), p.getNombre(), p.getEspecie() == null ? null : p.getEspecie().getId(),
                p.getTipoConsulta(), p.getSubjetivo(), p.getObjetivo(), p.getEvaluacion(), p.getPlan());
    }
}
