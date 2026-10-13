package com.veterinaria.clinica.api.dto;

import com.veterinaria.clinica.domain.Diagnostico;

public record DiagnosticoResponse(
        Long id,
        Long catalogoId,
        String codigoCatalogo,
        String descripcion,
        String codigoCie,
        String tipo,
        int orden,
        String lateralidad,
        boolean resuelto) {

    public static DiagnosticoResponse from(Diagnostico d) {
        return new DiagnosticoResponse(d.getId(), d.getCatalogo() == null ? null : d.getCatalogo().getId(),
                d.getCatalogo() == null ? null : d.getCatalogo().getCodigo(), d.getDescripcion(), d.getCodigoCie(),
                d.getTipo(), d.getOrden(), d.getLateralidad(), d.isResuelto());
    }
}
