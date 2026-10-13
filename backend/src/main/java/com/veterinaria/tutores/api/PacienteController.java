package com.veterinaria.tutores.api;

import org.springframework.data.domain.Sort;
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

import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;
import com.veterinaria.tutores.api.dto.PacienteRequest;
import com.veterinaria.tutores.api.dto.PacienteResponse;
import com.veterinaria.tutores.application.PacienteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pacientes")
@Tag(name = "Pacientes", description = "Pacientes (mascotas) de la clinica")
public class PacienteController {

    private final PacienteService pacienteService;

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }

    @GetMapping
    @Operation(summary = "Lista pacientes con busqueda y paginacion")
    public PageResponse<PacienteResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pacienteService.list(q, PageRequests.of(page, size, Sort.by("nombre")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve un paciente por id")
    public PacienteResponse find(@PathVariable Long id) {
        return pacienteService.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un nuevo paciente")
    public PacienteResponse create(@Valid @RequestBody PacienteRequest request) {
        return pacienteService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un paciente")
    public PacienteResponse update(@PathVariable Long id, @Valid @RequestBody PacienteRequest request) {
        return pacienteService.update(id, request);
    }
}
