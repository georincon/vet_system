package com.veterinaria.clinica.api.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.veterinaria.clinica.domain.ConsultaVacuna;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Vacuna de una consulta (entrada y salida). Ignora campos desconocidos que reenvia el cliente. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultaVacunaDto(
        Long id,
        @NotBlank(message = "El nombre de la vacuna es obligatorio")
        @Size(max = 120, message = "El nombre de la vacuna no puede exceder 120 caracteres") String nombre,
        @Size(max = 60, message = "El lote no puede exceder 60 caracteres") String lote,
        LocalDate fechaAplicacion,
        LocalDate proximaFecha) {

    public static ConsultaVacunaDto from(ConsultaVacuna v) {
        return new ConsultaVacunaDto(v.getId(), v.getNombre(), v.getLote(), v.getFechaAplicacion(),
                v.getProximaFecha());
    }
}
