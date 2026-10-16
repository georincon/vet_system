package com.veterinaria.clinica.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.clinica.api.dto.PlanDtos.ControlRequest;
import com.veterinaria.clinica.api.dto.PlanDtos.ControlResponse;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanRequest;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanResponse;
import com.veterinaria.clinica.application.PlanTratamientoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Plan de tratamiento y controles de seguimiento de una consulta (lectura ADMIN/VETERINARIO/AUXILIAR; escritura ADMIN/VETERINARIO). */
@RestController
@RequestMapping("/api/consultas/{consultaId}/plan-tratamiento")
@Tag(name = "Plan de tratamiento", description = "Plan de tratamiento y controles de seguimiento")
public class PlanTratamientoController {

    private final PlanTratamientoService service;

    public PlanTratamientoController(PlanTratamientoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Plan de tratamiento de la consulta (con items y controles); 404 si no tiene")
    public PlanResponse obtener(@PathVariable Long consultaId) {
        return service.obtener(consultaId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea el plan (uno por consulta ABIERTA; 409 si ya existe o la consulta esta cerrada)")
    public PlanResponse crear(@PathVariable Long consultaId, @Valid @RequestBody PlanRequest request) {
        return service.crear(consultaId, request);
    }

    @PutMapping
    @Operation(summary = "Edita el plan; campos null no cambian, items null conserva y una lista reemplaza")
    public PlanResponse actualizar(@PathVariable Long consultaId, @Valid @RequestBody PlanRequest request) {
        return service.actualizar(consultaId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina el plan (solo consulta ABIERTA)")
    public void eliminar(@PathVariable Long consultaId) {
        service.eliminar(consultaId);
    }

    @GetMapping("/controles")
    @Operation(summary = "Controles de seguimiento de la consulta")
    public List<ControlResponse> controles(@PathVariable Long consultaId) {
        return service.controles(consultaId);
    }

    @PostMapping("/controles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un control (fecha sugerida y motivo); tambien en consulta CERRADA (deja un addendum SEGUIMIENTO)",
            description = "Con generarRecordatorio=true se pide a comunicaciones un recordatorio por correo; la respuesta trae citaSugerida para precargar la agenda.")
    public ControlResponse crearControl(@PathVariable Long consultaId, @Valid @RequestBody ControlRequest request) {
        return service.crearControl(consultaId, request);
    }

    @PutMapping("/controles/{controlId}")
    @Operation(summary = "Edita un control (fecha, motivo, estado, citaId)")
    public ControlResponse actualizarControl(@PathVariable Long consultaId, @PathVariable Long controlId,
            @Valid @RequestBody ControlRequest request) {
        return service.actualizarControl(consultaId, controlId, request);
    }

    @DeleteMapping("/controles/{controlId}")
    @Operation(summary = "Cancela el control (estado CANCELADO; no se borra)")
    public ControlResponse cancelarControl(@PathVariable Long consultaId, @PathVariable Long controlId) {
        return service.cancelarControl(consultaId, controlId);
    }
}
