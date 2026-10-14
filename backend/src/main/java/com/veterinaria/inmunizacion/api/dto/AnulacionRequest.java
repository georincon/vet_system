package com.veterinaria.inmunizacion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnulacionRequest(
        @NotBlank(message = "El motivo es obligatorio") @Size(max = 255, message = "Maximo 255 caracteres") String motivo) {
}
