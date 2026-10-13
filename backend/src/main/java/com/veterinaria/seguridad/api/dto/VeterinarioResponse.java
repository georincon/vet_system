package com.veterinaria.seguridad.api.dto;

import com.veterinaria.seguridad.domain.Usuario;

/** Veterinario activo para desplegables (id y nombre visible). */
public record VeterinarioResponse(Long id, String nombre) {

    public static VeterinarioResponse from(Usuario u) {
        return new VeterinarioResponse(u.getId(), u.getNombre());
    }
}
