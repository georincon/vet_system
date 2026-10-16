package com.veterinaria.seguridad.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.seguridad.api.dto.PerfilProfesional;
import com.veterinaria.seguridad.application.PerfilProfesionalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@Tag(name = "Perfil profesional", description = "Tarjeta profesional del veterinario (aparece en la formula y los informes)")
public class PerfilProfesionalController {

    public record PerfilRequest(
            @Size(max = 50, message = "El numero profesional no puede exceder 50 caracteres") String numeroProfesional,
            @Size(max = 120, message = "La especialidad no puede exceder 120 caracteres") String especialidad) {
    }

    private final PerfilProfesionalService service;

    public PerfilProfesionalController(PerfilProfesionalService service) {
        this.service = service;
    }

    @GetMapping("/api/perfil/profesional")
    @Operation(summary = "Mi perfil profesional (ADMIN, VETERINARIO)")
    public PerfilProfesional propio() {
        return service.propio();
    }

    @PutMapping("/api/perfil/profesional")
    @Operation(summary = "Actualiza mi tarjeta profesional y especialidad (ADMIN, VETERINARIO); campos null no cambian")
    public PerfilProfesional actualizarPropio(@Valid @RequestBody PerfilRequest r) {
        return service.propioActualizar(r.numeroProfesional(), r.especialidad());
    }

    @PutMapping("/api/usuarios/{id}/perfil-profesional")
    @Operation(summary = "Actualiza la tarjeta profesional de un usuario VETERINARIO/ADMIN (solo ADMIN)")
    public PerfilProfesional actualizar(@PathVariable Long id, @Valid @RequestBody PerfilRequest r) {
        return service.actualizar(id, r.numeroProfesional(), r.especialidad());
    }
}
