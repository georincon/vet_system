package com.veterinaria.citas.api.dto;

import java.time.LocalDate;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada de la lista de espera. En el alta es obligatorio el paciente; {@code tutorId} por defecto es el
 * tutor principal del paciente. {@code prioridad}: 1 (mas urgente) a 5, por defecto 3.
 * {@code franjaPreferida}: MANANA, TARDE o CUALQUIERA. {@code estado} (solo edicion): ACTIVA, ASIGNADA, CANCELADA o VENCIDA.
 * En la edicion los campos ausentes (null) no se modifican y un texto vacio limpia las notas.
 */
public record ListaEsperaRequest(
        @NotNull(groups = Alta.class, message = "El paciente es obligatorio") Long pacienteId,
        Long tutorId,
        Long tipoCitaId,
        Long veterinarioId,
        LocalDate fechaDeseadaDesde,
        LocalDate fechaDeseadaHasta,
        @Pattern(regexp = "MANANA|TARDE|CUALQUIERA", message = "Franja no valida") String franjaPreferida,
        @Min(value = 1, message = "La prioridad minima es 1") @Max(value = 5, message = "La prioridad maxima es 5") Integer prioridad,
        @Pattern(regexp = "ACTIVA|ASIGNADA|CANCELADA|VENCIDA", message = "Estado no valido") String estado,
        @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres") String notas) {
}
