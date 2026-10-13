package com.veterinaria.seguridad.api.dto;

import com.veterinaria.seguridad.domain.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de usuario. {@code activo} es opcional (por defecto activo). */
public record CrearUsuarioRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 255, message = "El usuario no puede exceder 255 caracteres") String username,
        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 6, max = 72, message = "La contrasena debe tener entre 6 y 72 caracteres") String password,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 255, message = "El nombre no puede exceder 255 caracteres") String nombre,
        @Email(message = "Correo invalido")
        @Size(max = 255, message = "El correo no puede exceder 255 caracteres") String email,
        @NotNull(message = "El rol es obligatorio") Rol rol,
        Boolean activo) {
}
