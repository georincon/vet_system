package com.veterinaria.inventario.api;

import java.util.List;

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

import com.veterinaria.inventario.api.dto.ItemDtos.BodegaRequest;
import com.veterinaria.inventario.api.dto.ItemDtos.BodegaResponse;
import com.veterinaria.inventario.api.dto.ItemDtos.CategoriaRequest;
import com.veterinaria.inventario.api.dto.ItemDtos.CategoriaResponse;
import com.veterinaria.inventario.api.dto.ItemDtos.ItemRequest;
import com.veterinaria.inventario.api.dto.ItemDtos.ItemResponse;
import com.veterinaria.inventario.application.ItemService;
import com.veterinaria.shared.web.Alta;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inventario")
@Tag(name = "Inventario - catalogo", description = "Items/servicios, categorias y bodegas")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping("/items")
    @Operation(summary = "Lista items/servicios (q, tipo, categoriaId, controlaStock, esControlado, activo, bajoMinimo)")
    public PageResponse<ItemResponse> items(@RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo, @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Boolean controlaStock, @RequestParam(required = false) Boolean esControlado,
            @RequestParam(required = false) Boolean activo, @RequestParam(required = false) Boolean bajoMinimo,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(q, tipo, categoriaId, controlaStock, esControlado, activo, bajoMinimo,
                PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/items/{id}")
    public ItemResponse item(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un item/servicio (ADMIN)")
    public ItemResponse crear(@Validated(Alta.class) @RequestBody ItemRequest r) {
        return service.crear(r);
    }

    @PutMapping("/items/{id}")
    @Operation(summary = "Edita un item (ADMIN, parcial); activo=false lo da de baja")
    public ItemResponse actualizar(@PathVariable Long id, @Valid @RequestBody ItemRequest r) {
        return service.actualizar(id, r);
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias(@RequestParam(required = false) String tipo,
            @RequestParam(required = false) Boolean activo) {
        return service.categorias(tipo, activo);
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una categoria (ADMIN)")
    public CategoriaResponse crearCategoria(@Validated(Alta.class) @RequestBody CategoriaRequest r) {
        return service.crearCategoria(r);
    }

    @PutMapping("/categorias/{id}")
    public CategoriaResponse actualizarCategoria(@PathVariable Long id, @Valid @RequestBody CategoriaRequest r) {
        return service.actualizarCategoria(id, r);
    }

    @GetMapping("/bodegas")
    public List<BodegaResponse> bodegas(@RequestParam(required = false) Boolean activo) {
        return service.bodegas(activo);
    }

    @PostMapping("/bodegas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una bodega (ADMIN)")
    public BodegaResponse crearBodega(@Validated(Alta.class) @RequestBody BodegaRequest r) {
        return service.crearBodega(r);
    }

    @PutMapping("/bodegas/{id}")
    public BodegaResponse actualizarBodega(@PathVariable Long id, @Valid @RequestBody BodegaRequest r) {
        return service.actualizarBodega(id, r);
    }
}
