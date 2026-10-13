package com.veterinaria.clinica.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.clinica.api.dto.CatalogoDiagnosticoResponse;
import com.veterinaria.clinica.api.dto.PlantillaResponse;
import com.veterinaria.clinica.application.CatalogoClinicoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/clinica")
@Tag(name = "Clinica", description = "Catalogos clinicos de lectura")
public class ClinicaCatalogoController {

    private final CatalogoClinicoService service;

    public ClinicaCatalogoController(CatalogoClinicoService service) {
        this.service = service;
    }

    @GetMapping("/plantillas")
    @Operation(summary = "Plantillas SOAP activas; con especieId trae las de esa especie y las generales")
    public List<PlantillaResponse> plantillas(@RequestParam(required = false) Long especieId) {
        return service.plantillas(especieId);
    }

    @GetMapping("/diagnosticos")
    @Operation(summary = "Catalogo de diagnosticos frecuentes; q filtra por nombre o codigo")
    public List<CatalogoDiagnosticoResponse> diagnosticos(@RequestParam(required = false) String q) {
        return service.diagnosticos(q);
    }
}
