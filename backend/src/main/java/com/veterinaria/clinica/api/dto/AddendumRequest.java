package com.veterinaria.clinica.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code tipo}: ACLARACION (defecto), CORRECCION, RESULTADO_TARDIO o SEGUIMIENTO. */
public record AddendumRequest(
        @Pattern(regexp = "ACLARACION|CORRECCION|RESULTADO_TARDIO|SEGUIMIENTO", message = "Tipo de addendum no valido") String tipo,
        @NotBlank(message = "El texto es obligatorio")
        @Size(max = 10000, message = "El texto no puede exceder 10000 caracteres") String texto) {
}
