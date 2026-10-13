package com.veterinaria.clinica.api.dto;

import com.veterinaria.clinica.domain.CatalogoDiagnostico;

public record CatalogoDiagnosticoResponse(Long id, String codigo, String nombre, String categoria) {

    public static CatalogoDiagnosticoResponse from(CatalogoDiagnostico d) {
        return new CatalogoDiagnosticoResponse(d.getId(), d.getCodigo(), d.getNombre(), d.getCategoria());
    }
}
