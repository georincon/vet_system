package com.veterinaria.seguridad.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.seguridad.api.dto.VeterinarioResponse;
import com.veterinaria.seguridad.domain.Rol;
import com.veterinaria.seguridad.internal.UsuarioRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/veterinarios")
@Tag(name = "Veterinarios", description = "Listado de veterinarios para desplegables (cualquier rol autenticado)")
public class VeterinarioController {

    private final UsuarioRepository usuarioRepository;

    public VeterinarioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    @Operation(summary = "Usuarios activos con rol VETERINARIO, ordenados por nombre")
    public List<VeterinarioResponse> list() {
        return usuarioRepository.findByRolAndActivoTrueOrderByNombreAsc(Rol.VETERINARIO).stream()
                .map(VeterinarioResponse::from).toList();
    }
}
