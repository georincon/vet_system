package com.veterinaria.comunicaciones.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.comunicaciones.api.dto.PlantillaRequest;
import com.veterinaria.comunicaciones.api.dto.PlantillaResponse;
import com.veterinaria.comunicaciones.application.PlantillaService;
import com.veterinaria.shared.web.Alta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/comunicaciones/plantillas")
@Tag(name = "Comunicaciones", description = "Plantillas, reglas, recordatorios y bitacora")
public class PlantillaController {

    private final PlantillaService service;

    public PlantillaController(PlantillaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista plantillas (filtros: canal, activo)")
    public List<PlantillaResponse> listar(@RequestParam(required = false) String canal,
            @RequestParam(required = false) Boolean activo) {
        return service.listar(canal, activo);
    }

    @GetMapping("/{id}")
    public PlantillaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una plantilla (ADMIN); variables: tutor, paciente, vacuna, fecha, hora, clinica, sede")
    public PlantillaResponse crear(@Validated(Alta.class) @RequestBody PlantillaRequest r) {
        return service.crear(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita una plantilla (ADMIN, parcial; activo=false la desactiva)")
    public PlantillaResponse actualizar(@PathVariable Long id, @Valid @RequestBody PlantillaRequest r) {
        return service.actualizar(id, r);
    }
}
