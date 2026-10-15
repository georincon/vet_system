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

import com.veterinaria.comunicaciones.api.dto.ReglaRequest;
import com.veterinaria.comunicaciones.api.dto.ReglaResponse;
import com.veterinaria.comunicaciones.application.ReglaService;
import com.veterinaria.shared.web.Alta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/comunicaciones/reglas")
@Tag(name = "Comunicaciones")
public class ReglaController {

    private final ReglaService service;

    public ReglaController(ReglaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista reglas de recordatorio (filtros: evento, activo)")
    public List<ReglaResponse> listar(@RequestParam(required = false) String evento,
            @RequestParam(required = false) Boolean activo) {
        return service.listar(evento, activo);
    }

    @GetMapping("/{id}")
    public ReglaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una regla (ADMIN)")
    public ReglaResponse crear(@Validated(Alta.class) @RequestBody ReglaRequest r) {
        return service.crear(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita una regla (ADMIN, parcial; activo=false la desactiva)")
    public ReglaResponse actualizar(@PathVariable Long id, @Valid @RequestBody ReglaRequest r) {
        return service.actualizar(id, r);
    }
}
