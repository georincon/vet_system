package com.veterinaria.clinica.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.veterinaria.clinica.domain.ConsultaMedicamento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Medicamento de una consulta (entrada y salida). Ignora campos desconocidos que reenvia el cliente. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultaMedicamentoDto(
        Long id,
        @NotBlank(message = "El nombre del medicamento es obligatorio")
        @Size(max = 150, message = "El nombre del medicamento no puede exceder 150 caracteres") String nombre,
        @Size(max = 120, message = "La dosis no puede exceder 120 caracteres") String dosis,
        @Size(max = 120, message = "La frecuencia no puede exceder 120 caracteres") String frecuencia,
        @Size(max = 120, message = "La duracion no puede exceder 120 caracteres") String duracion,
        @Size(max = 50, message = "La via no puede exceder 50 caracteres") String via,
        @Size(max = 255, message = "Las observaciones no pueden exceder 255 caracteres") String observaciones,
        Integer orden) {

    public static ConsultaMedicamentoDto from(ConsultaMedicamento m) {
        return new ConsultaMedicamentoDto(m.getId(), m.getNombre(), m.getDosis(), m.getFrecuencia(),
                m.getDuracion(), m.getVia(), m.getObservaciones(), m.getOrden());
    }
}
