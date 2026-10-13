package com.veterinaria.clinica.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.clinica.api.dto.ConsultaResponse;
import com.veterinaria.clinica.api.dto.IniciarConsultaRequest;
import com.veterinaria.clinica.application.ConsultaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/citas")
@Tag(name = "Consultas", description = "Historias clinicas y consultas")
public class CitaConsultaController {

    private final ConsultaService consultaService;

    public CitaConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @PostMapping("/{id}/iniciar-consulta")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inicia la consulta de una cita (ADMIN, VETERINARIO)",
            description = "Crea la consulta ABIERTA con paciente, veterinario y motivo de la cita y pone la cita EN_CURSO. "
                    + "Cuerpo opcional: {plantillaId, tipoConsulta}. 409 si la cita ya tiene consulta o esta "
                    + "cancelada/atendida.")
    public ConsultaResponse iniciar(@PathVariable Long id,
            @Valid @RequestBody(required = false) IniciarConsultaRequest request) {
        return consultaService.iniciarDesdeCita(id, request);
    }
}
