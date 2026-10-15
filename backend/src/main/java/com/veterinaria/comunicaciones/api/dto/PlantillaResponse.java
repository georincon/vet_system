package com.veterinaria.comunicaciones.api.dto;

import java.util.List;

public record PlantillaResponse(
        Long id,
        String codigo,
        String canal,
        String idioma,
        String asunto,
        String cuerpo,
        List<String> variables,
        boolean activo) {
}
