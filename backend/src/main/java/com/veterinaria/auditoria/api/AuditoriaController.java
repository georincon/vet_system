package com.veterinaria.auditoria.api;

import java.time.LocalDate;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.auditoria.api.dto.AuditoriaResponse;
import com.veterinaria.auditoria.application.AuditoriaService;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "Auditoria", description = "Bitacora de cambios (solo ADMIN)")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Busca en la bitacora, mas reciente primero",
            description = "Filtros opcionales: entidad (nombre de tabla: tutor, paciente, consulta, receta, cita, factura...), "
                    + "entidadId, usuarioId, desde/hasta (YYYY-MM-DD, hasta inclusivo).")
    public PageResponse<AuditoriaResponse> buscar(
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) Long entidadId,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.buscar(entidad, entidadId, usuarioId, desde, hasta,
                PageRequests.of(page, size, Sort.unsorted()));
    }
}
