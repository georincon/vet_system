package com.veterinaria.facturacion.api;

import java.time.LocalDate;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaResumen;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.NotaCreditoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.NotaCreditoResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.PagoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.VentaPosRequest;
import com.veterinaria.facturacion.application.FacturaService;
import com.veterinaria.facturacion.application.NotaCreditoService;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facturacion")
@Tag(name = "Facturacion", description = "Facturas, POS, pagos y notas credito (ADMIN, RECEPCION; notas credito solo ADMIN)")
public class FacturaController {

    private final FacturaService facturas;
    private final NotaCreditoService notas;

    public FacturaController(FacturaService facturas, NotaCreditoService notas) {
        this.facturas = facturas;
        this.notas = notas;
    }

    @GetMapping("/facturas")
    @Operation(summary = "Lista documentos (tutorId, pacienteId, estado, tipo, q=numero, desde, hasta, conSaldo)")
    public PageResponse<FacturaResumen> listar(@RequestParam(required = false) Long tutorId,
            @RequestParam(required = false) Long pacienteId, @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Boolean conSaldo, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return facturas.listar(tutorId, pacienteId, estado, tipo, q, desde, hasta, conSaldo,
                PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/facturas/{id}")
    public FacturaResponse factura(@PathVariable Long id) {
        return facturas.obtener(id);
    }

    @PostMapping("/facturas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emite una factura (items) o factura un presupuesto (presupuestoId); descuenta stock FEFO y aplica pagos")
    public FacturaResponse crear(@Valid @RequestBody FacturaRequest r) {
        return facturas.crear(r);
    }

    @PostMapping("/facturas/{id}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un pago/abono (requiere turno de caja abierto del usuario); devuelve la factura")
    public FacturaResponse pagar(@PathVariable Long id, @Valid @RequestBody PagoRequest r) {
        return facturas.pagar(id, r);
    }

    @PostMapping("/facturas/{id}/notas-credito")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nota credito total o parcial: unica via para anular/corregir (ADMIN)")
    public NotaCreditoResponse notaCredito(@PathVariable Long id, @Valid @RequestBody NotaCreditoRequest r) {
        return notas.acreditar(id, r);
    }

    @PostMapping("/facturas/{id}/dian/reintentar")
    @Operation(summary = "Vuelve a solicitar la emision electronica al puerto DIAN (ADMIN)")
    public FacturaResponse reintentarDian(@PathVariable Long id) {
        return facturas.reintentarDian(id);
    }

    @PostMapping("/pos/ventas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Venta de mostrador (Consumidor final si no hay tutorId); pagos mixtos que cuadren con el total")
    public FacturaResponse pos(@Valid @RequestBody VentaPosRequest r) {
        return facturas.pos(r);
    }
}
