package com.veterinaria.citas.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.veterinaria.citas.api.dto.CambioEstadoRequest;
import com.veterinaria.citas.api.dto.CitaRequest;
import com.veterinaria.citas.api.dto.CitaResponse;
import com.veterinaria.citas.api.dto.EstadoCitaResponse;
import com.veterinaria.citas.application.CitaService;
import com.veterinaria.shared.web.Alta;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/citas")
@Tag(name = "Citas", description = "Agenda y citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping("/estados")
    @Operation(summary = "Lista estados de cita")
    public List<EstadoCitaResponse> estados() {
        return citaService.estados();
    }

    @GetMapping
    @Operation(summary = "Lista citas activas, opcionalmente de una fecha, con paginacion")
    public PageResponse<CitaResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return citaService.list(fecha, PageRequests.of(page, size, Sort.by("fecha", "horaInicio")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve una cita por id")
    public CitaResponse find(@PathVariable Long id) {
        return citaService.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una nueva cita")
    public CitaResponse create(@Validated(Alta.class) @RequestBody CitaRequest request) {
        return citaService.create(request);
    }

    @PostMapping("/{id}/estado")
    @Operation(summary = "Cambia el estado de la cita (check-in, sala de espera, cancelacion...)",
            description = "Transiciones validas: PROGRAMADA->CONFIRMADA|EN_SALA|CANCELADA|NO_ASISTIO|REAGENDADA; "
                    + "CONFIRMADA->EN_SALA|CANCELADA|NO_ASISTIO|REAGENDADA; EN_SALA->EN_CURSO|CANCELADA|NO_ASISTIO; "
                    + "EN_CURSO->ATENDIDA. Cualquier otra devuelve 409. EN_SALA registra la hora de llegada.")
    public CitaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request) {
        return citaService.cambiarEstado(id, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza una cita",
            description = "Edicion parcial: un campo ausente o null NO se modifica; una cadena vacia (\"\") en un campo "
                    + "de texto (motivo, notas) LIMPIA el valor. Para quitar la sala envie quitarSala=true.")
    public CitaResponse update(@PathVariable Long id, @Valid @RequestBody CitaRequest request) {
        return citaService.update(id, request);
    }
}
