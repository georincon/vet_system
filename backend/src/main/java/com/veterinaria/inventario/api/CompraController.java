package com.veterinaria.inventario.api;

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

import com.veterinaria.inventario.api.dto.CompraDtos.OrdenRequest;
import com.veterinaria.inventario.api.dto.CompraDtos.OrdenResponse;
import com.veterinaria.inventario.api.dto.CompraDtos.ProveedorRequest;
import com.veterinaria.inventario.api.dto.CompraDtos.ProveedorResponse;
import com.veterinaria.inventario.api.dto.CompraDtos.RecepcionRequest;
import com.veterinaria.inventario.api.dto.CompraDtos.RecepcionResponse;
import com.veterinaria.inventario.application.CompraService;
import com.veterinaria.inventario.application.ProveedorService;
import com.veterinaria.shared.web.Alta;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Proveedores, ordenes de compra y recepciones: solo ADMIN (definido en SecurityConfig). */
@RestController
@RequestMapping("/api/inventario")
@Tag(name = "Inventario - compras", description = "Proveedores, ordenes de compra y recepciones (ADMIN)")
public class CompraController {

    private final ProveedorService proveedores;
    private final CompraService compras;

    public CompraController(ProveedorService proveedores, CompraService compras) {
        this.proveedores = proveedores;
        this.compras = compras;
    }

    @GetMapping("/proveedores")
    public PageResponse<ProveedorResponse> proveedores(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return proveedores.listar(q, activo, PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/proveedores/{id}")
    public ProveedorResponse proveedor(@PathVariable Long id) {
        return proveedores.obtener(id);
    }

    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    public ProveedorResponse crearProveedor(@Validated(Alta.class) @RequestBody ProveedorRequest r) {
        return proveedores.crear(r);
    }

    @PutMapping("/proveedores/{id}")
    public ProveedorResponse actualizarProveedor(@PathVariable Long id, @Valid @RequestBody ProveedorRequest r) {
        return proveedores.actualizar(id, r);
    }

    @GetMapping("/ordenes-compra")
    public PageResponse<OrdenResponse> ordenes(@RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) String estado, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return compras.ordenes(proveedorId, estado, PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/ordenes-compra/{id}")
    public OrdenResponse orden(@PathVariable Long id) {
        return compras.orden(id);
    }

    @PostMapping("/ordenes-compra")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una orden de compra en BORRADOR")
    public OrdenResponse crearOrden(@Validated(Alta.class) @RequestBody OrdenRequest r) {
        return compras.crearOrden(r);
    }

    @PutMapping("/ordenes-compra/{id}")
    @Operation(summary = "Edita una orden en BORRADOR (items reemplaza el detalle)")
    public OrdenResponse actualizarOrden(@PathVariable Long id, @Valid @RequestBody OrdenRequest r) {
        return compras.actualizarOrden(id, r);
    }

    @PostMapping("/ordenes-compra/{id}/enviar")
    public OrdenResponse enviar(@PathVariable Long id) {
        return compras.enviar(id);
    }

    @PostMapping("/ordenes-compra/{id}/cancelar")
    public OrdenResponse cancelar(@PathVariable Long id) {
        return compras.cancelar(id);
    }

    @PostMapping("/ordenes-compra/{id}/recepciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recibe (total o parcial) una orden: crea lotes y entradas de kardex")
    public RecepcionResponse recibirOrden(@PathVariable Long id, @Valid @RequestBody RecepcionRequest r) {
        if (r.ordenCompraId() != null && !r.ordenCompraId().equals(id)) {
            throw new com.veterinaria.shared.error.BusinessException("ordenCompraId no coincide con la ruta");
        }
        return compras.recibir(new RecepcionRequest(id, r.proveedorId(), r.bodegaId(), r.numeroFacturaProveedor(),
                r.fecha(), r.fechaVencimientoPago(), r.notas(), r.items()));
    }

    @GetMapping("/recepciones")
    public PageResponse<RecepcionResponse> recepciones(@RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) Long ordenCompraId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return compras.recepciones(proveedorId, ordenCompraId, PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/recepciones/{id}")
    public RecepcionResponse recepcion(@PathVariable Long id) {
        return compras.recepcion(id);
    }

    @PostMapping("/recepciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recepcion directa (sin orden): proveedorId + items con itemId y costoUnitario")
    public RecepcionResponse recibir(@Valid @RequestBody RecepcionRequest r) {
        return compras.recibir(r);
    }
}
