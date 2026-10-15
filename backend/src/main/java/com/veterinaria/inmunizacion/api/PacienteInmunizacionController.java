package com.veterinaria.inmunizacion.api;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.inmunizacion.TipoAplicacion;
import com.veterinaria.inmunizacion.api.dto.AnulacionRequest;
import com.veterinaria.inmunizacion.api.dto.InmunizacionRequest;
import com.veterinaria.inmunizacion.api.dto.InmunizacionResponse;
import com.veterinaria.inmunizacion.api.dto.SugerenciaResponse;
import com.veterinaria.inmunizacion.application.CarnetService;
import com.veterinaria.inmunizacion.application.InmunizacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pacientes/{pacienteId}")
@Tag(name = "Inmunizacion", description = "Aplicaciones, esquema sugerido y carnet del paciente")
public class PacienteInmunizacionController {

    private final InmunizacionService service;
    private final CarnetService carnetService;

    public PacienteInmunizacionController(InmunizacionService service, CarnetService carnetService) {
        this.service = service;
        this.carnetService = carnetService;
    }

    @PostMapping("/inmunizaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una vacuna o antiparasitario; valida/descuenta lote y calcula la proxima fecha")
    public InmunizacionResponse registrar(@PathVariable Long pacienteId, @Valid @RequestBody InmunizacionRequest r) {
        return service.registrar(pacienteId, r);
    }

    @GetMapping("/inmunizaciones")
    @Operation(summary = "Vacunas y antiparasitarios del paciente (mas reciente primero)")
    public List<InmunizacionResponse> listar(@PathVariable Long pacienteId,
            @RequestParam(required = false) TipoAplicacion tipo,
            @RequestParam(defaultValue = "false") boolean incluirAnuladas) {
        return service.listar(pacienteId, tipo, incluirAnuladas);
    }

    @PostMapping("/inmunizaciones/{id}/anular")
    @Operation(summary = "Anula (no borra) una aplicacion con motivo; devuelve el stock del lote")
    public InmunizacionResponse anular(@PathVariable Long pacienteId, @PathVariable Long id,
            @RequestParam(defaultValue = "VACUNA") TipoAplicacion tipo, @Valid @RequestBody AnulacionRequest r) {
        return service.anular(pacienteId, tipo, id, r.motivo());
    }

    @GetMapping("/esquema-sugerido")
    @Operation(summary = "Que le toca al paciente segun especie, edad y aplicadas")
    public SugerenciaResponse sugerido(@PathVariable Long pacienteId) {
        return service.sugerido(pacienteId);
    }

    @GetMapping(value = "/carnet.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Carnet de vacunacion y desparasitacion en PDF")
    public ResponseEntity<byte[]> carnet(@PathVariable Long pacienteId) {
        CarnetService.Carnet carnet = carnetService.generar(pacienteId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(carnet.numero() + ".pdf").build().toString())
                .body(carnet.pdf());
    }
}
