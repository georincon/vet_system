package com.veterinaria.procedimientos.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.procedimientos.api.dto.ProcDtos.ConsentimientoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ConsentimientoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.EstadoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.MonitoreoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.MonitoreoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoResumen;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoUpdate;
import com.veterinaria.procedimientos.application.ProcedimientoService;
import com.veterinaria.procedimientos.application.ReporteQuirurgicoService;
import com.veterinaria.procedimientos.application.ReporteQuirurgicoService.Reporte;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Procedimientos y cirugia", description = "Registro clinico, monitoreo anestesico, consentimiento y reporte PDF")
public class ProcedimientoController {

    private final ProcedimientoService service;
    private final ReporteQuirurgicoService reporte;

    public ProcedimientoController(ProcedimientoService service, ReporteQuirurgicoService reporte) {
        this.service = service;
        this.reporte = reporte;
    }

    @PostMapping("/procedimientos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un procedimiento ambulatorio o una cirugia (ADMIN, VETERINARIO)")
    public ProcedimientoResponse crear(@Valid @RequestBody ProcedimientoRequest request) {
        return service.crear(request);
    }

    @GetMapping("/procedimientos")
    @Operation(summary = "Lista procedimientos/cirugias (filtros: paciente, consulta, tipo, estado)")
    public PageResponse<ProcedimientoResumen> listar(@RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long consultaId, @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listar(pacienteId, consultaId, tipo, estado, PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/procedimientos/{id}")
    public ProcedimientoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/procedimientos/{id}")
    @Operation(summary = "Edita el registro (campos ausentes sin cambio; 'insumos' reemplaza la lista)")
    public ProcedimientoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProcedimientoUpdate request) {
        return service.actualizar(id, request);
    }

    @PostMapping("/procedimientos/{id}/estado")
    @Operation(summary = "Cambia el estado (PROGRAMADA, EN_CURSO, REALIZADA, CANCELADA, APLAZADA); cirugia exige consentimiento firmado")
    public ProcedimientoResponse estado(@PathVariable Long id, @Valid @RequestBody EstadoRequest request) {
        return service.cambiarEstado(id, request);
    }

    @PostMapping("/procedimientos/{id}/monitoreo-anestesico")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una toma del monitoreo anestesico (ADMIN, VETERINARIO, AUXILIAR)")
    public MonitoreoResponse monitoreo(@PathVariable Long id, @Valid @RequestBody MonitoreoRequest request) {
        return service.registrarMonitoreo(id, request);
    }

    @GetMapping("/procedimientos/{id}/monitoreo-anestesico")
    public List<MonitoreoResponse> monitoreos(@PathVariable Long id) {
        return service.monitoreo(id);
    }

    @PostMapping("/procedimientos/{id}/consentimiento")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra el consentimiento informado (aceptado o rechazado) con archivo firmado opcional")
    public ConsentimientoResponse consentimiento(@PathVariable Long id, @Valid @RequestBody ConsentimientoRequest request) {
        return service.registrarConsentimiento(id, request);
    }

    @GetMapping("/procedimientos/{id}/reporte.pdf")
    @Operation(summary = "Reporte quirurgico / de procedimiento en PDF (ADMIN, VETERINARIO)")
    public ResponseEntity<byte[]> reportePdf(@PathVariable Long id) {
        Reporte r = reporte.generar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("reporte-" + r.codigo() + ".pdf", StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(r.pdf());
    }

    @GetMapping("/pacientes/{pacienteId}/procedimientos")
    @Operation(summary = "Procedimientos y cirugias del paciente")
    public PageResponse<ProcedimientoResumen> delPaciente(@PathVariable Long pacienteId,
            @RequestParam(required = false) String tipo, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listar(pacienteId, null, tipo, null, PageRequests.of(page, size, Sort.unsorted()));
    }
}
