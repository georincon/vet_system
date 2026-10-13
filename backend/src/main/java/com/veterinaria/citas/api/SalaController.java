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

import com.veterinaria.citas.api.dto.SalaRequest;
import com.veterinaria.citas.api.dto.SalaResponse;
import com.veterinaria.citas.application.SalaService;
import com.veterinaria.shared.web.Alta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agenda/salas")
@Tag(name = "Agenda", description = "Calendario, salas, bloqueos y lista de espera")
public class SalaController {

    private final SalaService salaService;

    public SalaController(SalaService salaService) {
        this.salaService = salaService;
    }

    @GetMapping
    @Operation(summary = "Lista salas (solo activas salvo todas=true)")
    public List<SalaResponse> list(@RequestParam(defaultValue = "false") boolean todas) {
        return salaService.list(todas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve una sala")
    public SalaResponse find(@PathVariable Long id) {
        return salaService.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una sala (ADMIN, RECEPCION)")
    public SalaResponse create(@Validated(Alta.class) @RequestBody SalaRequest request) {
        return salaService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza una sala; los campos ausentes no se modifican")
    public SalaResponse update(@PathVariable Long id, @Valid @RequestBody SalaRequest request) {
        return salaService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactiva la sala (baja logica)")
    public void delete(@PathVariable Long id) {
        salaService.desactivar(id);
    }
}
