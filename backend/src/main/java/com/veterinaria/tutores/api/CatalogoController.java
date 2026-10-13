package com.veterinaria.tutores.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.tutores.api.dto.EspecieResponse;
import com.veterinaria.tutores.api.dto.RazaResponse;
import com.veterinaria.tutores.application.CatalogoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/catalogos")
@Tag(name = "Catalogos", description = "Especies y razas para pacientes")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/especies")
    @Operation(summary = "Lista las especies activas")
    public List<EspecieResponse> especies() {
        return catalogoService.especies();
    }

    @GetMapping("/razas")
    @Operation(summary = "Lista las razas activas, opcionalmente de una especie")
    public List<RazaResponse> razas(@RequestParam(required = false) Long especieId) {
        return catalogoService.razas(especieId);
    }
}
