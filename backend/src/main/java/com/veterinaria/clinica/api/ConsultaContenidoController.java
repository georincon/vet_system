package com.veterinaria.clinica.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.clinica.api.dto.DiagnosticoRequest;
import com.veterinaria.clinica.api.dto.DiagnosticoResponse;
import com.veterinaria.clinica.api.dto.RecetaRequest;
import com.veterinaria.clinica.api.dto.RecetaResponse;
import com.veterinaria.clinica.api.dto.SignoVitalRequest;
import com.veterinaria.clinica.api.dto.SignoVitalResponse;
import com.veterinaria.clinica.application.ConsultaDetalleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Signos vitales, diagnosticos y recetas de una consulta ABIERTA (409 si esta cerrada). */
@RestController
@RequestMapping("/api/consultas/{consultaId}")
@Tag(name = "Consultas", description = "Historias clinicas y consultas")
public class ConsultaContenidoController {

    private final ConsultaDetalleService service;

    public ConsultaContenidoController(ConsultaDetalleService service) {
        this.service = service;
    }

    @PostMapping("/signos-vitales")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una toma de signos vitales (ADMIN, VETERINARIO, AUXILIAR)",
            description = "Si incluye pesoKg tambien crea un registro de peso y actualiza el peso actual del paciente.")
    public SignoVitalResponse agregarSigno(@PathVariable Long consultaId, @Valid @RequestBody SignoVitalRequest request) {
        return service.agregarSigno(consultaId, request);
    }

    @PutMapping("/signos-vitales/{signoId}")
    @Operation(summary = "Edita una toma de signos vitales; los campos ausentes no se modifican")
    public SignoVitalResponse actualizarSigno(@PathVariable Long consultaId, @PathVariable Long signoId,
            @Valid @RequestBody SignoVitalRequest request) {
        return service.actualizarSigno(consultaId, signoId, request);
    }

    @DeleteMapping("/signos-vitales/{signoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una toma de signos vitales (solo consulta ABIERTA)")
    public void eliminarSigno(@PathVariable Long consultaId, @PathVariable Long signoId) {
        service.eliminarSigno(consultaId, signoId);
    }

    @PostMapping("/diagnosticos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega un diagnostico (del catalogo o libre)")
    public DiagnosticoResponse agregarDiagnostico(@PathVariable Long consultaId,
            @Valid @RequestBody DiagnosticoRequest request) {
        return service.agregarDiagnostico(consultaId, request);
    }

    @PutMapping("/diagnosticos/{dxId}")
    @Operation(summary = "Edita un diagnostico; los campos ausentes no se modifican")
    public DiagnosticoResponse actualizarDiagnostico(@PathVariable Long consultaId, @PathVariable Long dxId,
            @Valid @RequestBody DiagnosticoRequest request) {
        return service.actualizarDiagnostico(consultaId, dxId, request);
    }

    @DeleteMapping("/diagnosticos/{dxId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un diagnostico (solo consulta ABIERTA)")
    public void eliminarDiagnostico(@PathVariable Long consultaId, @PathVariable Long dxId) {
        service.eliminarDiagnostico(consultaId, dxId);
    }

    @PostMapping("/recetas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emite una receta con sus items")
    public RecetaResponse agregarReceta(@PathVariable Long consultaId, @Valid @RequestBody RecetaRequest request) {
        return service.agregarReceta(consultaId, request);
    }

    @PutMapping("/recetas/{recetaId}")
    @Operation(summary = "Edita una receta; items null conserva las lineas, una lista las reemplaza")
    public RecetaResponse actualizarReceta(@PathVariable Long consultaId, @PathVariable Long recetaId,
            @Valid @RequestBody RecetaRequest request) {
        return service.actualizarReceta(consultaId, recetaId, request);
    }

    @DeleteMapping("/recetas/{recetaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anula la receta (estado CANCELADA)")
    public void cancelarReceta(@PathVariable Long consultaId, @PathVariable Long recetaId) {
        service.cancelarReceta(consultaId, recetaId);
    }
}
