package com.veterinaria.inmunizacion.api.dto;

import java.util.List;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta (obligatorios codigo, nombre, vacunaId) y edicion parcial de un esquema. {@code dosis}, si se envia,
 * reemplaza el calendario (se actualiza por {@code orden}; las que no vengan se eliminan).
 */
public record EsquemaRequest(
        @NotBlank(groups = Alta.class, message = "El codigo es obligatorio")
        @Size(max = 40, message = "El codigo no puede exceder 40 caracteres") String codigo,
        @NotBlank(groups = Alta.class, message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre no puede exceder 200 caracteres") String nombre,
        @NotNull(groups = Alta.class, message = "La vacuna es obligatoria") Long vacunaId,
        Long especieId,
        @Size(max = 120) String fuente,
        String notas,
        Boolean activo,
        @Valid List<Dosis> dosis) {

    public record Dosis(
            @NotNull(message = "El orden es obligatorio") @Min(value = 1, message = "El orden debe ser >= 1") Integer orden,
            @NotBlank(message = "El nombre de la dosis es obligatorio") @Size(max = 60) String nombreDosis,
            @Min(0) Integer edadMinimaSemanas,
            @Min(0) Integer intervaloDiasPrevia,
            Boolean esRefuerzo,
            @Min(1) Integer repetirCadaDias) {
    }
}
