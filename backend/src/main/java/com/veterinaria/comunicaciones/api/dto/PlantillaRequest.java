package com.veterinaria.comunicaciones.api.dto;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Plantilla de mensaje. Variables permitidas en asunto/cuerpo: {{tutor}}, {{paciente}}, {{vacuna}}, {{fecha}},
 * {{hora}}, {{clinica}}, {{sede}}. Alta: obligatorios codigo, canal y cuerpo.
 */
public record PlantillaRequest(
        @NotBlank(groups = Alta.class, message = "El codigo es obligatorio")
        @Size(max = 50, message = "El codigo no puede exceder 50 caracteres") String codigo,
        @NotNull(groups = Alta.class, message = "El canal es obligatorio")
        @Pattern(regexp = "EMAIL|WHATSAPP|SMS|PUSH", message = "Canal: EMAIL, WHATSAPP, SMS o PUSH") String canal,
        @Size(max = 5) String idioma,
        @Size(max = 200, message = "El asunto no puede exceder 200 caracteres") String asunto,
        @NotBlank(groups = Alta.class, message = "El cuerpo es obligatorio") String cuerpo,
        Boolean activo) {
}
