package com.veterinaria.citas.api.dto;

import java.time.LocalDateTime;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Bloqueo de agenda. Debe indicar {@code veterinarioId} y/o {@code salaId}. {@code inicio}/{@code fin} son
 * fecha-hora local de la clinica (sin zona, p. ej. 2026-10-20T08:00:00). En la edicion los campos ausentes no cambian.
 * {@code tipo}: VACACIONES, CAPACITACION, ALMUERZO, MANTENIMIENTO, CIRUGIA, PERSONAL u OTRO.
 */
public record BloqueoRequest(
        Long veterinarioId,
        Long salaId,
        @NotNull(groups = Alta.class, message = "El inicio es obligatorio") LocalDateTime inicio,
        @NotNull(groups = Alta.class, message = "El fin es obligatorio") LocalDateTime fin,
        @Pattern(regexp = "VACACIONES|CAPACITACION|ALMUERZO|MANTENIMIENTO|CIRUGIA|PERSONAL|OTRO",
                message = "Tipo de bloqueo no valido") String tipo,
        @Size(max = 255, message = "El motivo no puede exceder 255 caracteres") String motivo) {
}
