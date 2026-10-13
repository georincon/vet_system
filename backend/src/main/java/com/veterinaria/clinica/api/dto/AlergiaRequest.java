package com.veterinaria.clinica.api.dto;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alergia del paciente. {@code severidad}: BAJA (defecto), MEDIA o ALTA. {@code tipo}: MEDICAMENTO, ALIMENTO,
 * AMBIENTAL, VACUNA u OTRA (defecto). En PUT los campos ausentes no se modifican y "" limpia reaccion.
 */
public record AlergiaRequest(
        @NotBlank(groups = Alta.class, message = "La descripcion es obligatoria")
        @Size(max = 255, message = "La descripcion no puede exceder 255 caracteres") String descripcion,
        @Pattern(regexp = "BAJA|MEDIA|ALTA", message = "Severidad no valida") String severidad,
        @Pattern(regexp = "MEDICAMENTO|ALIMENTO|AMBIENTAL|VACUNA|OTRA", message = "Tipo de alergia no valido") String tipo,
        @Size(max = 255, message = "La reaccion no puede exceder 255 caracteres") String reaccion,
        Boolean activo) {
}
