package com.veterinaria.seguridad.application;

import org.springframework.stereotype.Component;

import com.veterinaria.seguridad.domain.Rol;
import com.veterinaria.seguridad.domain.Usuario;
import com.veterinaria.seguridad.internal.UsuarioRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;

/** Resuelve el usuario que actua como veterinario de una cita o consulta. */
@Component
public class VeterinarioLookup {

    private final UsuarioRepository usuarioRepository;

    public VeterinarioLookup(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** @throws BusinessException si el usuario no tiene rol VETERINARIO o esta inactivo */
    public Usuario obtener(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (usuario.getRol() != Rol.VETERINARIO || !usuario.isActivo()) {
            throw new BusinessException("El usuario seleccionado no es un veterinario");
        }
        return usuario;
    }
}
