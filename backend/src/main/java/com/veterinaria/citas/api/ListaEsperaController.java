package com.veterinaria.citas.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.citas.api.dto.ListaEsperaRequest;
import com.veterinaria.citas.api.dto.ListaEsperaResponse;
import com.veterinaria.citas.application.ListaEsperaService;
import com.veterinaria.shared.web.Alta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/agenda/lista-espera")
@Validated
@Tag(name = "Agenda", description = "Calendario, salas, bloqueos y lista de espera")
public class ListaEsperaController {

    private final ListaEsperaService service;

    public ListaEsperaController(ListaEsperaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista de espera ordenada por prioridad y antiguedad; filtro opcional por estado")
    public List<ListaEsperaResponse> list(
            @RequestParam(required = false) @Pattern(regexp = "ACTIVA|ASIGNADA|CANCELADA|VENCIDA") String estado) {
        return service.list(estado);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve una entrada de la lista de espera")
    public ListaEsperaResponse find(@PathVariable Long id) {
        return service.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega un paciente a la lista de espera")
    public ListaEsperaResponse create(@Validated(Alta.class) @RequestBody ListaEsperaRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza una entrada; los campos ausentes no se modifican")
    public ListaEsperaResponse update(@PathVariable Long id, @Valid @RequestBody ListaEsperaRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/asignar")
    @Operation(summary = "Marca la entrada como ASIGNADA a una cita existente (citaId)")
    public ListaEsperaResponse asignar(@PathVariable Long id, @RequestParam Long citaId) {
        return service.asignar(id, citaId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancela la entrada (baja logica, estado CANCELADA)")
    public void delete(@PathVariable Long id) {
        service.cancelar(id);
    }
}
