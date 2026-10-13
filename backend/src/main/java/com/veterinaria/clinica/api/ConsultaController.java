package com.veterinaria.clinica.api;

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

import com.veterinaria.clinica.api.dto.AddendumRequest;
import com.veterinaria.clinica.api.dto.AddendumResponse;
import com.veterinaria.clinica.api.dto.ConsultaRequest;
import com.veterinaria.clinica.api.dto.ConsultaResponse;
import com.veterinaria.clinica.application.ConsultaDetalleService;
import com.veterinaria.clinica.application.ConsultaService;
import com.veterinaria.shared.web.Alta;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/consultas")
@Validated
@Tag(name = "Consultas", description = "Historias clinicas y consultas")
public class ConsultaController {

    private final ConsultaService consultaService;
    private final ConsultaDetalleService detalleService;

    public ConsultaController(ConsultaService consultaService, ConsultaDetalleService detalleService) {
        this.consultaService = consultaService;
        this.detalleService = detalleService;
    }

    @GetMapping
    @Operation(summary = "Lista consultas activas con filtros opcionales (fecha, pacienteId, estado) y paginacion")
    public PageResponse<ConsultaResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) @Pattern(regexp = "ABIERTA|CERRADA", message = "Estado no valido") String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return consultaService.list(fecha, pacienteId, estado,
                PageRequests.of(page, size, Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve una consulta con signos vitales, diagnosticos y recetas")
    public ConsultaResponse find(@PathVariable Long id) {
        return consultaService.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una nueva consulta (queda ABIERTA)")
    public ConsultaResponse create(@Validated(Alta.class) @RequestBody ConsultaRequest request) {
        return consultaService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza una consulta ABIERTA",
            description = "Edicion parcial: un campo ausente o null NO se modifica; una cadena vacia (\"\") en un campo de "
                    + "texto LIMPIA el valor. medicamentos/vacunas: null conserva la lista, una lista la reemplaza. "
                    + "Si la consulta esta CERRADA responde 409 (use addendums).")
    public ConsultaResponse update(@PathVariable Long id, @Valid @RequestBody ConsultaRequest request) {
        return consultaService.update(id, request);
    }

    @PostMapping("/{id}/cerrar")
    @Operation(summary = "Cierra la consulta (inmutable desde entonces) y marca la cita como ATENDIDA",
            description = "409 si ya esta cerrada. Calcula firmaHash (SHA-256) del contenido clinico.")
    public ConsultaResponse cerrar(@PathVariable Long id) {
        return consultaService.cerrar(id);
    }

    @PostMapping("/{id}/addendums")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega un addendum (nota posterior) a una consulta CERRADA",
            description = "Append-only: no se pueden editar ni borrar. 422 si la consulta aun esta ABIERTA.")
    public AddendumResponse agregarAddendum(@PathVariable Long id, @Valid @RequestBody AddendumRequest request) {
        return detalleService.agregarAddendum(id, request);
    }

    @GetMapping("/{id}/addendums")
    @Operation(summary = "Lista los addendums de la consulta, del mas antiguo al mas reciente")
    public List<AddendumResponse> addendums(@PathVariable Long id) {
        return detalleService.addendums(id);
    }
}
