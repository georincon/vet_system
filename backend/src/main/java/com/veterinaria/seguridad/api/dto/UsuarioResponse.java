package com.veterinaria.seguridad.api.dto;

import com.veterinaria.seguridad.domain.Rol;
import com.veterinaria.seguridad.domain.Usuario;

public record UsuarioResponse(Long id, String username, String nombre, String email, Rol rol, boolean activo) {

    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getUsername(), u.getNombre(), u.getEmail(), u.getRol(), u.isActivo());
    }
}
