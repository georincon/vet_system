package com.veterinaria.citas.api.dto;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta/edicion de sala. En la edicion los campos ausentes (null) no se modifican.
 * {@code tipo}: CONSULTORIO, QUIROFANO, HOSPITALIZACION, PELUQUERIA, IMAGENES, LABORATORIO, GUARDERIA o ESPERA.
 */
public record SalaRequest(
        @NotBlank(groups = Alta.class, message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres") String nombre,
        @Pattern(regexp = "CONSULTORIO|QUIROFANO|HOSPITALIZACION|PELUQUERIA|IMAGENES|LABORATORIO|GUARDERIA|ESPERA",
                message = "Tipo de sala no valido") String tipo,
        @Min(value = 1, message = "La capacidad debe ser al menos 1") Integer capacidad,
        Boolean activo) {
}
