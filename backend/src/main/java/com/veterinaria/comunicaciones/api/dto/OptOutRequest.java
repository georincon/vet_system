package com.veterinaria.comunicaciones.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Baja manual de comunicaciones (el tutor lo pidio por telefono/mostrador). */
public record OptOutRequest(
        @NotNull(message = "El tutor es obligatorio") Long tutorId,
        @Pattern(regexp = "EMAIL|WHATSAPP|SMS|PUSH|TODOS", message = "Canal: EMAIL, WHATSAPP, SMS, PUSH o TODOS") String canal,
        @Pattern(regexp = "TODAS|SERVICIO|MARKETING", message = "Categoria: TODAS, SERVICIO o MARKETING") String categoria,
        @Size(max = 255) String motivo) {
}
