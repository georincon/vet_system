package com.veterinaria.facturacion.api;

import org.springframework.data.domain.Sort;
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

import com.veterinaria.facturacion.api.dto.FacturacionDtos.EstadoPresupuestoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturarPresupuestoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.PresupuestoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.PresupuestoResponse;
import com.veterinaria.facturacion.application.FacturaService;
import com.veterinaria.facturacion.application.PresupuestoService;
import com.veterinaria.shared.web.Alta;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facturacion/presupuestos")
@Tag(name = "Facturacion - presupuestos", description = "Presupuestos (lectura tambien VETERINARIO, solo de sus consultas)")
public class PresupuestoController {

    private final PresupuestoService presupuestos;
    private final FacturaService facturas;

    public PresupuestoController(PresupuestoService presupuestos, FacturaService facturas) {
        this.presupuestos = presupuestos;
        this.facturas = facturas;
    }

    @GetMapping
    public PageResponse<PresupuestoResponse> listar(@RequestParam(required = false) Long tutorId,
            @RequestParam(required = false) Long pacienteId, @RequestParam(required = false) Long consultaId,
            @RequestParam(required = false) String estado, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return presupuestos.listar(tutorId, pacienteId, consultaId, estado, PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/{id}")
    public PresupuestoResponse obtener(@PathVariable Long id) {
        return presupuestos.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PresupuestoResponse crear(@Validated(Alta.class) @RequestBody PresupuestoRequest r) {
        return presupuestos.crear(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita un presupuesto en BORRADOR (items reemplaza los renglones)")
    public PresupuestoResponse actualizar(@PathVariable Long id, @Valid @RequestBody PresupuestoRequest r) {
        return presupuestos.actualizar(id, r);
    }

    @PostMapping("/{id}/estado")
    public PresupuestoResponse estado(@PathVariable Long id, @Valid @RequestBody EstadoPresupuestoRequest r) {
        return presupuestos.cambiarEstado(id, r.estado());
    }

    @PostMapping("/{id}/facturar")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convierte el presupuesto en factura (descuenta stock FEFO al emitir) y lo marca FACTURADO")
    public FacturaResponse facturar(@PathVariable Long id, @Valid @RequestBody(required = false) FacturarPresupuestoRequest r) {
        return facturas.facturarPresupuesto(id, r == null ? new FacturarPresupuestoRequest(null, null, null, null, null) : r);
    }
}
