package com.veterinaria.citas.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.veterinaria.citas.api.dto.BloqueoRequest;
import com.veterinaria.citas.api.dto.BloqueoResponse;
import com.veterinaria.citas.application.BloqueoService;
import com.veterinaria.shared.web.Alta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agenda/bloqueos")
@Tag(name = "Agenda", description = "Calendario, salas, bloqueos y lista de espera")
public class BloqueoController {

    private final BloqueoService bloqueoService;

    public BloqueoController(BloqueoService bloqueoService) {
        this.bloqueoService = bloqueoService;
    }

    @GetMapping
    @Operation(summary = "Bloqueos que tocan el rango [desde, hasta] (hasta inclusivo)")
    public List<BloqueoResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long veterinarioId,
            @RequestParam(required = false) Long salaId) {
        return bloqueoService.list(desde, hasta, veterinarioId, salaId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve un bloqueo")
    public BloqueoResponse find(@PathVariable Long id) {
        return bloqueoService.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un bloqueo; 409 si ya hay citas vigentes en el rango")
    public BloqueoResponse create(@Validated(Alta.class) @RequestBody BloqueoRequest request) {
        return bloqueoService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un bloqueo; los campos ausentes no se modifican")
    public BloqueoResponse update(@PathVariable Long id, @Valid @RequestBody BloqueoRequest request) {
        return bloqueoService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un bloqueo")
    public void delete(@PathVariable Long id) {
        bloqueoService.delete(id);
    }
}
