package com.veterinaria.citas.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.citas.api.dto.AgendaResponse;
import com.veterinaria.citas.api.dto.TipoCitaResponse;
import com.veterinaria.citas.application.AgendaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/agenda")
@Tag(name = "Agenda", description = "Calendario, salas, bloqueos y lista de espera")
public class AgendaController {

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping
    @Operation(summary = "Citas y bloqueos de un rango para el calendario",
            description = "desde/hasta (YYYY-MM-DD, hasta inclusivo, maximo 62 dias). Filtros opcionales veterinarioId y salaId. "
                    + "Por defecto excluye citas canceladas/no asistio/reagendadas (incluirCanceladas=true las trae).")
    public AgendaResponse agenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long veterinarioId,
            @RequestParam(required = false) Long salaId,
            @RequestParam(defaultValue = "false") boolean incluirCanceladas) {
        return agendaService.agenda(desde, hasta, veterinarioId, salaId, incluirCanceladas);
    }

    @GetMapping("/tipos-cita")
    @Operation(summary = "Tipos de cita activos con su duracion por defecto")
    public List<TipoCitaResponse> tiposCita() {
        return agendaService.tiposCita();
    }
}
