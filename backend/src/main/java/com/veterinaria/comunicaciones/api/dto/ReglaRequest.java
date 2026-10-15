package com.veterinaria.comunicaciones.api.dto;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Regla de recordatorio. {@code diasOffset} negativo = antes del evento (-7: una semana antes), positivo = despues.
 * El job genera recordatorios para los eventos VACUNA_PROXIMA, DESPARASITACION_PROXIMA y CITA.
 * Alta: obligatorios codigo, evento, diasOffset, canal y plantillaId.
 */
public record ReglaRequest(
        @NotBlank(groups = Alta.class, message = "El codigo es obligatorio")
        @Size(max = 50, message = "El codigo no puede exceder 50 caracteres") String codigo,
        @NotNull(groups = Alta.class, message = "El evento es obligatorio")
        @Pattern(regexp = "CITA|VACUNA_PROXIMA|DESPARASITACION_PROXIMA|CONTROL|CUMPLEANOS|FACTURA_VENCIDA|POST_CONSULTA|POST_CIRUGIA|PLAN_SALUD_RENOVACION|INACTIVIDAD",
                message = "Evento no valido") String evento,
        @NotNull(groups = Alta.class, message = "diasOffset es obligatorio") Integer diasOffset,
        @NotNull(groups = Alta.class, message = "El canal es obligatorio")
        @Pattern(regexp = "EMAIL|WHATSAPP|SMS|PUSH", message = "Canal: EMAIL, WHATSAPP, SMS o PUSH") String canal,
        @NotNull(groups = Alta.class, message = "La plantilla es obligatoria") Long plantillaId,
        Boolean activo) {
}
