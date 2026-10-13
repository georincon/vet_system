package com.veterinaria.citas.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta/edicion de cita. En el alta son obligatorios paciente, veterinario, fecha y hora de inicio;
 * en la edicion los campos ausentes (null) no se modifican y un texto vacio limpia motivo/notas.
 * Si no se envia {@code horaFin} se usa la duracion por defecto del {@code tipoCitaId} (30 min sin tipo).
 * {@code quitarSala=true} desasigna la sala en la edicion. Un cambio de {@code estadoCitaId} debe ser
 * una transicion valida (409 si no); para el flujo de sala use {@code POST /api/citas/{id}/estado}.
 */
public record CitaRequest(
        @NotNull(groups = Alta.class, message = "El paciente es obligatorio") Long pacienteId,
        @NotNull(groups = Alta.class, message = "El veterinario es obligatorio") Long veterinarioId,
        @NotNull(groups = Alta.class, message = "La fecha es obligatoria") LocalDate fecha,
        @NotNull(groups = Alta.class, message = "La hora de inicio es obligatoria") LocalTime horaInicio,
        LocalTime horaFin,
        Long tipoCitaId,
        Long salaId,
        Boolean quitarSala,
        @Size(max = 255, message = "El motivo no puede exceder 255 caracteres") String motivo,
        @Size(max = 1000, message = "Las notas no pueden exceder 1000 caracteres") String notas,
        Integer estadoCitaId) {
}
