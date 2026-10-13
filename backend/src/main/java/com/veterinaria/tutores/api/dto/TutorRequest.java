package com.veterinaria.tutores.api.dto;

import com.veterinaria.tutores.domain.TipoDocumento;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta/edicion de tutor. {@code activo} es opcional (por defecto activo al crear; sin cambio al editar). */
public record TutorRequest(
        @NotNull(message = "El tipo de documento es obligatorio") TipoDocumento tipoDocumento,
        @NotBlank(message = "El numero de documento es obligatorio")
        @Size(max = 30, message = "El numero de documento no puede exceder 30 caracteres") String numeroDocumento,
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 120, message = "Los nombres no pueden exceder 120 caracteres") String nombres,
        @Size(max = 120, message = "Los apellidos no pueden exceder 120 caracteres") String apellidos,
        @Size(max = 30, message = "El telefono no puede exceder 30 caracteres") String telefono,
        @Size(max = 30, message = "El telefono alternativo no puede exceder 30 caracteres") String telefonoAlt,
        @Email(message = "Correo invalido")
        @Size(max = 255, message = "El correo no puede exceder 255 caracteres") String email,
        @Size(max = 255, message = "La direccion no puede exceder 255 caracteres") String direccion,
        @Size(max = 120, message = "La ciudad no puede exceder 120 caracteres") String ciudad,
        String notas,
        Boolean activo) {
}
