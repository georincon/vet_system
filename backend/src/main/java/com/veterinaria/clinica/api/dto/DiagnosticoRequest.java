package com.veterinaria.clinica.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Diagnostico de la consulta: del catalogo ({@code catalogoId}; la descripcion se toma del catalogo si no se envia)
 * o libre ({@code descripcion}). {@code tipo}: PRESUNTIVO (defecto), DIFERENCIAL o DEFINITIVO.
 * En PUT los campos ausentes (null) no se modifican.
 */
public record DiagnosticoRequest(
        Long catalogoId,
        @Size(max = 2000, message = "La descripcion no puede exceder 2000 caracteres") String descripcion,
        @Size(max = 20, message = "El codigo CIE no puede exceder 20 caracteres") String codigoCie,
        @Pattern(regexp = "PRESUNTIVO|DIFERENCIAL|DEFINITIVO", message = "Tipo de diagnostico no valido") String tipo,
        @Min(value = 1, message = "El orden minimo es 1") Integer orden,
        @Size(max = 10, message = "La lateralidad no puede exceder 10 caracteres") String lateralidad,
        Boolean resuelto) {
}
