package com.veterinaria.inventario.api;

import java.time.LocalDate;
import java.util.List;

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

import com.veterinaria.inventario.api.dto.StockDtos.AjusteRequest;
import com.veterinaria.inventario.api.dto.StockDtos.AjusteResponse;
import com.veterinaria.inventario.api.dto.StockDtos.AlertasResponse;
import com.veterinaria.inventario.api.dto.StockDtos.ControladoResponse;
import com.veterinaria.inventario.api.dto.StockDtos.EstadoLoteRequest;
import com.veterinaria.inventario.api.dto.StockDtos.LoteRequest;
import com.veterinaria.inventario.api.dto.StockDtos.LoteResponse;
import com.veterinaria.inventario.api.dto.StockDtos.MovimientoResponse;
import com.veterinaria.inventario.api.dto.StockDtos.RetiroLoteRequest;
import com.veterinaria.inventario.api.dto.StockDtos.SalidaRequest;
import com.veterinaria.inventario.api.dto.StockDtos.SalidaResponse;
import com.veterinaria.inventario.api.dto.StockDtos.UsoRequest;
import com.veterinaria.inventario.application.AlertaService;
import com.veterinaria.inventario.application.ControladoService;
import com.veterinaria.inventario.application.LoteService;
import com.veterinaria.inventario.application.MovimientoService;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inventario")
@Tag(name = "Inventario - existencias", description = "Lotes, kardex, salidas/ajustes, alertas y libro de controlados")
public class StockController {

    private final LoteService lotes;
    private final MovimientoService movimientos;
    private final AlertaService alertas;
    private final ControladoService controlados;

    public StockController(LoteService lotes, MovimientoService movimientos, AlertaService alertas,
            ControladoService controlados) {
        this.lotes = lotes;
        this.movimientos = movimientos;
        this.alertas = alertas;
        this.controlados = controlados;
    }

    // ---- lotes
    @GetMapping("/lotes")
    @Operation(summary = "Lotes por FEFO: itemId | vacunaId | antiparasitarioId, bodegaId, soloVigentes, conStock, q")
    public List<LoteResponse> lotes(@RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long vacunaId, @RequestParam(required = false) Long antiparasitarioId,
            @RequestParam(required = false) Long bodegaId, @RequestParam(defaultValue = "false") boolean soloVigentes,
            @RequestParam(required = false) Boolean conStock, @RequestParam(required = false) String q) {
        return lotes.listar(itemId, vacunaId, antiparasitarioId, bodegaId, soloVigentes, conStock, q);
    }

    @GetMapping("/lotes/{id}")
    public LoteResponse lote(@PathVariable Long id) {
        return lotes.obtener(id);
    }

    @PostMapping("/lotes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Carga inicial de un lote con su ENTRADA (ADMIN); para compras use /recepciones")
    public LoteResponse crearLote(@Valid @RequestBody LoteRequest r) {
        return lotes.crear(r);
    }

    @PostMapping("/lotes/{id}/estado")
    @Operation(summary = "Pone un lote en CUARENTENA o lo devuelve a DISPONIBLE (ADMIN)")
    public LoteResponse estado(@PathVariable Long id, @Valid @RequestBody EstadoLoteRequest r) {
        return lotes.cambiarEstado(id, r.estado());
    }

    @PostMapping("/lotes/{id}/retirar")
    @Operation(summary = "Da de baja las existencias de un lote vencido/danado (ADMIN)")
    public LoteResponse retirar(@PathVariable Long id, @Valid @RequestBody RetiroLoteRequest r) {
        return lotes.retirar(id, r.motivo());
    }

    // ---- kardex y movimientos
    @GetMapping("/movimientos")
    @Operation(summary = "Kardex paginado (itemId, loteId, tipo, desde, hasta, pacienteId, consultaId, referencia)")
    public PageResponse<MovimientoResponse> kardex(@RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long loteId, @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long pacienteId, @RequestParam(required = false) Long consultaId,
            @RequestParam(required = false) String referencia, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return movimientos.kardex(itemId, loteId, tipo, desde, hasta, pacienteId, consultaId, referencia,
                PageRequests.of(page, size, Sort.unsorted()));
    }

    @PostMapping("/movimientos/uso")
    @Operation(summary = "Uso clinico: descuenta FEFO (o de un lote) - ADMIN, VETERINARIO, AUXILIAR")
    public SalidaResponse uso(@Valid @RequestBody UsoRequest r) {
        return movimientos.uso(r);
    }

    @PostMapping("/movimientos/salida")
    @Operation(summary = "Salida con motivo (merma, dano, desecho, consumo...) - ADMIN")
    public SalidaResponse salida(@Valid @RequestBody SalidaRequest r) {
        return movimientos.salida(r);
    }

    @PostMapping("/movimientos/ajuste")
    @Operation(summary = "Ajuste por conteo fisico o diferencia con motivo - ADMIN")
    public AjusteResponse ajuste(@Valid @RequestBody AjusteRequest r) {
        return movimientos.ajustar(r);
    }

    // ---- alertas y libro
    @GetMapping("/alertas")
    @Operation(summary = "Bajo minimo, lotes por vencer en N dias (dias=30) y vencidos con existencias")
    public AlertasResponse alertas(@RequestParam(required = false) Integer dias) {
        return alertas.alertas(dias);
    }

    @GetMapping("/controlados")
    @Operation(summary = "Libro de medicamentos controlados (ADMIN, VETERINARIO)")
    public PageResponse<ControladoResponse> controlados(@RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long pacienteId, @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return controlados.libro(itemId, pacienteId, tipo, desde, hasta, PageRequests.of(page, size, Sort.unsorted()));
    }
}
