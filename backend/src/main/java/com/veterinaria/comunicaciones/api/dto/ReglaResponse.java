package com.veterinaria.comunicaciones.api.dto;

public record ReglaResponse(
        Long id,
        String codigo,
        String evento,
        int diasOffset,
        String canal,
        Long plantillaId,
        String plantillaCodigo,
        boolean activo,
        boolean canalDisponible) {
}
