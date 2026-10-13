package com.veterinaria.tutores.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.tutores.domain.Sexo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Alta/edicion de paciente. {@code tutorIds} es opcional: si se omite al editar, los
 * vinculos actuales no cambian; si se envia (aunque vacio), reemplaza el conjunto.
 */
public record PacienteRequest(
        @NotBlank(message = "El nombre del paciente es obligatorio")
        @Size(max = 120, message = "El nombre no puede exceder 120 caracteres") String nombre,
        @NotNull(message = "La especie es obligatoria") Long especieId,
        Long razaId,
        @NotNull(message = "El sexo es obligatorio") Sexo sexo,
        @Size(max = 30, message = "El estado reproductivo no puede exceder 30 caracteres") String estadoReproductivo,
        @Size(max = 60, message = "El color no puede exceder 60 caracteres") String color,
        @PastOrPresent(message = "La fecha de nacimiento no puede ser futura") LocalDate fechaNacimiento,
        @Size(max = 30, message = "El microchip no puede exceder 30 caracteres") String microchip,
        @DecimalMin(value = "0.0", message = "El peso no puede ser negativo") BigDecimal pesoActual,
        Boolean esterilizado,
        Boolean fallecido,
        String alertas,
        String notas,
        List<Long> tutorIds,
        Long tutorPrincipalId) {
}
