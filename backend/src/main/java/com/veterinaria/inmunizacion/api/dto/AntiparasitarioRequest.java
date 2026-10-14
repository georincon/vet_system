package com.veterinaria.inmunizacion.api.dto;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta (obligatorios codigo/nombre/tipo/intervaloDias) y edicion parcial de antiparasitarios. */
public record AntiparasitarioRequest(
        @NotBlank(groups = Alta.class, message = "El codigo es obligatorio")
        @Size(max = 50, message = "El codigo no puede exceder 50 caracteres") String codigo,
        @NotBlank(groups = Alta.class, message = "El nombre es obligatorio") String nombre,
        @NotNull(groups = Alta.class, message = "El tipo es obligatorio")
        @Pattern(regexp = "INTERNA|EXTERNA|DIROFILARIA|COMBINADA", message = "Tipo: INTERNA, EXTERNA, DIROFILARIA o COMBINADA")
        String tipo,
        @Size(max = 150) String principioActivo,
        Long especieId,
        @NotNull(groups = Alta.class, message = "El intervalo es obligatorio")
        @Min(value = 1, message = "El intervalo debe ser mayor a 0") Integer intervaloDias,
        Long itemId,
        Boolean activo) {
}
