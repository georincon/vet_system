package com.veterinaria.tutores.api;

import java.util.List;

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
import com.veterinaria.tutores.api.dto.PacienteResumen;
import com.veterinaria.tutores.api.dto.TutorRequest;
import com.veterinaria.tutores.api.dto.TutorResponse;
import com.veterinaria.tutores.application.TutorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tutores")
@Tag(name = "Tutores", description = "Tutores de los pacientes")
public class TutorController {

    private final TutorService tutorService;

    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    @GetMapping
    @Operation(summary = "Lista tutores con busqueda y paginacion")
    public PageResponse<TutorResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tutorService.list(q, PageRequests.of(page, size, Sort.by("apellidos", "nombres")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve un tutor por id")
    public TutorResponse find(@PathVariable Long id) {
        return tutorService.find(id);
    }

    @GetMapping("/{id}/pacientes")
    @Operation(summary = "Pacientes vinculados a un tutor")
    public List<PacienteResumen> pacientes(@PathVariable Long id) {
        return tutorService.pacientesDe(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un nuevo tutor")
    public TutorResponse create(@Valid @RequestBody TutorRequest request) {
        return tutorService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un tutor")
    public TutorResponse update(@PathVariable Long id, @Valid @RequestBody TutorRequest request) {
        return tutorService.update(id, request);
    }
}
