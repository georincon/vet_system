package com.veterinaria.clinica.api;

import java.util.List;

import org.springframework.data.domain.Sort;
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

import com.veterinaria.clinica.api.dto.AlergiaRequest;
import com.veterinaria.clinica.api.dto.AlergiaResponse;
import com.veterinaria.clinica.api.dto.HistoriaItem;
import com.veterinaria.clinica.api.dto.PesoRequest;
import com.veterinaria.clinica.api.dto.PesoResponse;
import com.veterinaria.clinica.application.PacienteClinicoService;
import com.veterinaria.shared.web.Alta;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/pacientes/{pacienteId}")
@Validated
@Tag(name = "Historia clinica", description = "Alergias, pesos y linea de tiempo del paciente")
public class PacienteClinicoController {

    private final PacienteClinicoService service;

    public PacienteClinicoController(PacienteClinicoService service) {
        this.service = service;
    }

    @GetMapping("/alergias")
    @Operation(summary = "Alergias activas del paciente (todas con incluirInactivas=true)")
    public List<AlergiaResponse> alergias(@PathVariable Long pacienteId,
            @RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return service.alergias(pacienteId, incluirInactivas);
    }

    @PostMapping("/alergias")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una alergia (ADMIN, VETERINARIO)")
    public AlergiaResponse agregarAlergia(@PathVariable Long pacienteId,
            @Validated(Alta.class) @RequestBody AlergiaRequest request) {
        return service.agregarAlergia(pacienteId, request);
    }

    @PutMapping("/alergias/{alergiaId}")
    @Operation(summary = "Edita una alergia; los campos ausentes no se modifican")
    public AlergiaResponse actualizarAlergia(@PathVariable Long pacienteId, @PathVariable Long alergiaId,
            @Valid @RequestBody AlergiaRequest request) {
        return service.actualizarAlergia(pacienteId, alergiaId, request);
    }

    @DeleteMapping("/alergias/{alergiaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactiva una alergia (baja logica)")
    public void desactivarAlergia(@PathVariable Long pacienteId, @PathVariable Long alergiaId) {
        service.desactivarAlergia(pacienteId, alergiaId);
    }

    @GetMapping("/pesos")
    @Operation(summary = "Historial de peso del paciente, del mas reciente al mas antiguo")
    public List<PesoResponse> pesos(@PathVariable Long pacienteId) {
        return service.pesos(pacienteId);
    }

    @PostMapping("/pesos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un peso y actualiza el peso actual del paciente (ADMIN, VETERINARIO, AUXILIAR)")
    public PesoResponse registrarPeso(@PathVariable Long pacienteId, @Valid @RequestBody PesoRequest request) {
        return service.registrarPeso(pacienteId, request);
    }

    @GetMapping("/historia")
    @Operation(summary = "Linea de tiempo paginada (consultas, vacunas, pesos, alergias), mas reciente primero")
    public PageResponse<HistoriaItem> historia(@PathVariable Long pacienteId,
            @RequestParam(required = false) @Pattern(regexp = "CONSULTA|VACUNA|PESO|ALERGIA", message = "Tipo no valido") String tipo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.historia(pacienteId, tipo, PageRequests.of(page, size, Sort.unsorted()));
    }
}
