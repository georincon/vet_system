package com.veterinaria.clinica.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.veterinaria.clinica.domain.RecetaItem;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Linea de receta (entrada y salida). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecetaItemDto(
        Long id,
        Long itemId,
        @NotBlank(message = "El medicamento es obligatorio")
        @Size(max = 255, message = "El medicamento no puede exceder 255 caracteres") String medicamento,
        @Size(max = 100, message = "La concentracion no puede exceder 100 caracteres") String concentracion,
        @Size(max = 100, message = "La presentacion no puede exceder 100 caracteres") String presentacion,
        @Size(max = 100, message = "La dosis no puede exceder 100 caracteres") String dosis,
        @Size(max = 60, message = "La via no puede exceder 60 caracteres") String via,
        @Size(max = 100, message = "La frecuencia no puede exceder 100 caracteres") String frecuencia,
        @Size(max = 100, message = "La duracion no puede exceder 100 caracteres") String duracion,
        @Min(value = 1, message = "La cantidad minima es 1") Integer cantidad,
        @Size(max = 1000, message = "Las indicaciones no pueden exceder 1000 caracteres") String indicaciones,
        Integer orden) {

    public static RecetaItemDto from(RecetaItem i) {
        return new RecetaItemDto(i.getId(), i.getItemId(), i.getMedicamento(), i.getConcentracion(), i.getPresentacion(), i.getDosis(), i.getVia(),
                i.getFrecuencia(), i.getDuracion(), i.getCantidad(), i.getIndicaciones(), i.getOrden());
    }
}
