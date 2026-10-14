package com.veterinaria.inmunizacion.api.dto;

import java.math.BigDecimal;

import com.veterinaria.inmunizacion.domain.Vacuna;

public record VacunaResponse(
        Long id,
        String codigo,
        String nombre,
        Long especieId,
        String especie,
        int periodoEficaciaDias,
        String clasificacion,
        String enfermedadesObjetivo,
        String fabricante,
        String viaAdministracion,
        BigDecimal dosisMl,
        Long itemId,
        boolean obligatoriaLey,
        boolean activo) {

    public static VacunaResponse from(Vacuna v) {
        return new VacunaResponse(v.getId(), v.getCodigo(), v.getNombre(),
                v.getEspecie() == null ? null : v.getEspecie().getId(),
                v.getEspecie() == null ? null : v.getEspecie().getNombre(), v.getPeriodoEficaciaDias(),
                v.getClasificacion(), v.getEnfermedadesObjetivo(), v.getFabricante(), v.getViaAdministracion(),
                v.getDosisMl(), v.getItemId(), v.isObligatoriaLey(), v.isActivo());
    }
}
