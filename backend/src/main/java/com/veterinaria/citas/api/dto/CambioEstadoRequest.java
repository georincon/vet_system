package com.veterinaria.citas.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de estado de una cita. {@code estado}: PROGRAMADA, CONFIRMADA, EN_SALA, EN_CURSO, ATENDIDA,
 * CANCELADA, NO_ASISTIO o REAGENDADA (alias aceptados: EN_CONSULTA=EN_CURSO, COMPLETADA=ATENDIDA).
 * {@code motivo} se guarda como motivo de cancelacion cuando el destino es CANCELADA/NO_ASISTIO/REAGENDADA.
 */
public record CambioEstadoRequest(
        @NotBlank(message = "El estado es obligatorio") String estado,
        @Size(max = 255, message = "El motivo no puede exceder 255 caracteres") String motivo) {
}
