package com.veterinaria.laboratorio.api;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenResumen;
import com.veterinaria.laboratorio.api.dto.LabDtos.TendenciaResponse;
import com.veterinaria.laboratorio.application.HistorialService;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/pacientes/{pacienteId}/laboratorio")
@Tag(name = "Laboratorio - paciente", description = "Historial de examenes y tendencia por parametro")
public class PacienteLaboratorioController {

    private final HistorialService service;

    public PacienteLaboratorioController(HistorialService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Historial de ordenes del paciente (laboratorio, imagen, patologia), filtrable por tipo de examen")
    public PageResponse<OrdenResumen> historial(@PathVariable Long pacienteId, @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.historial(pacienteId, tipo, PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/tendencia")
    @Operation(summary = "Serie historica de un analito (resultados validados) con resumen de variacion")
    public TendenciaResponse tendencia(@PathVariable Long pacienteId, @RequestParam Long parametroId) {
        return service.tendencia(pacienteId, parametroId);
    }
}
