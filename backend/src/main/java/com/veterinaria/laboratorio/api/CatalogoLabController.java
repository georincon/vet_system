package com.veterinaria.laboratorio.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.laboratorio.api.dto.LabDtos.ExamenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.ExamenResponse;
import com.veterinaria.laboratorio.application.ExamenCatalogoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/laboratorio/examenes")
@Tag(name = "Laboratorio - catalogo", description = "Examenes y paneles con parametros (LOINC/UCUM) y rangos de referencia")
public class CatalogoLabController {

    private final ExamenCatalogoService service;

    public CatalogoLabController(ExamenCatalogoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Catalogo de examenes (sin parametros; use el detalle). Filtros: tipo, q, soloActivos")
    public List<ExamenResponse> listar(@RequestParam(required = false) String tipo, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return service.listar(tipo, q, soloActivos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle del examen con parametros y rangos de referencia")
    public ExamenResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un examen/panel con sus parametros y rangos (ADMIN, VETERINARIO)")
    public ExamenResponse crear(@Valid @RequestBody ExamenRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza el examen; con 'parametros' sincroniza el panel (los ausentes se desactivan)")
    public ExamenResponse actualizar(@PathVariable Long id, @Valid @RequestBody ExamenRequest request) {
        return service.actualizar(id, request);
    }
}
