package com.veterinaria.clinica.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Registro de peso; {@code fecha} por defecto hoy (no puede ser futura). */
public record PesoRequest(
        @NotNull(message = "El peso es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El peso debe ser positivo")
        @DecimalMax(value = "999.99", message = "El peso excede el maximo") BigDecimal pesoKg,
        LocalDate fecha) {
}
