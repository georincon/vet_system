package com.veterinaria.inmunizacion.api.dto;

import java.math.BigDecimal;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta (POST, obligatorios codigo/nombre/periodoEficaciaDias) y edicion parcial (PUT) de vacunas. */
public record VacunaRequest(
        @NotBlank(groups = Alta.class, message = "El codigo es obligatorio")
        @Size(max = 50, message = "El codigo no puede exceder 50 caracteres") String codigo,
        @NotBlank(groups = Alta.class, message = "El nombre es obligatorio")
        @Size(max = 255, message = "El nombre no puede exceder 255 caracteres") String nombre,
        Long especieId,
        @NotNull(groups = Alta.class, message = "El periodo de eficacia es obligatorio")
        @Min(value = 1, message = "El periodo de eficacia debe ser mayor a 0") Integer periodoEficaciaDias,
        @Pattern(regexp = "NUCLEO|NO_NUCLEO|NO_RECOMENDADA", message = "Clasificacion: NUCLEO, NO_NUCLEO o NO_RECOMENDADA")
        String clasificacion,
        String enfermedadesObjetivo,
        @Size(max = 120) String fabricante,
        @Size(max = 20) String viaAdministracion,
        @DecimalMin(value = "0", message = "La dosis no puede ser negativa") BigDecimal dosisMl,
        Long itemId,
        Boolean obligatoriaLey,
        Boolean activo) {
}
