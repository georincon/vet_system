package com.veterinaria.inmunizacion.api;

import java.time.LocalDate;
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

import com.veterinaria.inmunizacion.TipoAplicacion;
import com.veterinaria.inmunizacion.api.dto.AntiparasitarioRequest;
import com.veterinaria.inmunizacion.api.dto.AntiparasitarioResponse;
import com.veterinaria.inmunizacion.api.dto.EsquemaRequest;
import com.veterinaria.inmunizacion.api.dto.EsquemaResponse;
import com.veterinaria.inmunizacion.api.dto.PendienteResponse;
import com.veterinaria.inmunizacion.api.dto.VacunaRequest;
import com.veterinaria.inmunizacion.api.dto.VacunaResponse;
import com.veterinaria.inmunizacion.application.CatalogoInmunizacionService;
import com.veterinaria.inmunizacion.application.PendientesService;
import com.veterinaria.shared.web.Alta;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inmunizacion")
@Tag(name = "Inmunizacion", description = "Catalogos de vacunas/antiparasitarios, esquemas y pendientes")
public class InmunizacionCatalogoController {

    private final CatalogoInmunizacionService catalogo;
    private final PendientesService pendientes;

    public InmunizacionCatalogoController(CatalogoInmunizacionService catalogo, PendientesService pendientes) {
        this.catalogo = catalogo;
        this.pendientes = pendientes;
    }

    // ---- vacunas
    @GetMapping("/vacunas")
    @Operation(summary = "Lista el catalogo de vacunas (filtros: especieId, activo, q)")
    public List<VacunaResponse> vacunas(@RequestParam(required = false) Long especieId,
            @RequestParam(required = false) Boolean activo, @RequestParam(required = false) String q) {
        return catalogo.vacunas(especieId, activo, q);
    }

    @GetMapping("/vacunas/{id}")
    public VacunaResponse vacuna(@PathVariable Long id) {
        return catalogo.vacuna(id);
    }

    @PostMapping("/vacunas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una vacuna (ADMIN)")
    public VacunaResponse crearVacuna(@Validated(Alta.class) @RequestBody VacunaRequest r) {
        return catalogo.crearVacuna(r);
    }

    @PutMapping("/vacunas/{id}")
    @Operation(summary = "Edita una vacuna (ADMIN, parcial)")
    public VacunaResponse actualizarVacuna(@PathVariable Long id, @Valid @RequestBody VacunaRequest r) {
        return catalogo.actualizarVacuna(id, r);
    }

    // ---- antiparasitarios
    @GetMapping("/antiparasitarios")
    @Operation(summary = "Lista el catalogo de antiparasitarios (filtros: especieId, activo)")
    public List<AntiparasitarioResponse> antiparasitarios(@RequestParam(required = false) Long especieId,
            @RequestParam(required = false) Boolean activo) {
        return catalogo.antiparasitarios(especieId, activo);
    }

    @PostMapping("/antiparasitarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un antiparasitario (ADMIN)")
    public AntiparasitarioResponse crearAntiparasitario(@Validated(Alta.class) @RequestBody AntiparasitarioRequest r) {
        return catalogo.crearAntiparasitario(r);
    }

    @PutMapping("/antiparasitarios/{id}")
    @Operation(summary = "Edita un antiparasitario (ADMIN, parcial)")
    public AntiparasitarioResponse actualizarAntiparasitario(@PathVariable Long id,
            @Valid @RequestBody AntiparasitarioRequest r) {
        return catalogo.actualizarAntiparasitario(id, r);
    }

    // ---- esquemas
    @GetMapping("/esquemas")
    @Operation(summary = "Lista esquemas vacunales con sus dosis (filtros: especieId, vacunaId, activo)")
    public List<EsquemaResponse> esquemas(@RequestParam(required = false) Long especieId,
            @RequestParam(required = false) Long vacunaId, @RequestParam(required = false) Boolean activo) {
        return catalogo.esquemas(especieId, vacunaId, activo);
    }

    @GetMapping("/esquemas/{id}")
    public EsquemaResponse esquema(@PathVariable Long id) {
        return catalogo.esquema(id);
    }

    @PostMapping("/esquemas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un esquema vacunal (ADMIN)")
    public EsquemaResponse crearEsquema(@Validated(Alta.class) @RequestBody EsquemaRequest r) {
        return catalogo.crearEsquema(r);
    }

    @PutMapping("/esquemas/{id}")
    @Operation(summary = "Edita un esquema; si se envia 'dosis' reemplaza el calendario (ADMIN)")
    public EsquemaResponse actualizarEsquema(@PathVariable Long id, @Valid @RequestBody EsquemaRequest r) {
        return catalogo.actualizarEsquema(id, r);
    }

    // ---- pendientes
    @GetMapping("/pendientes")
    @Operation(summary = "Proximas dosis pendientes (vencidas y por vencer) con tutor y contacto, paginado")
    public PageResponse<PendienteResponse> pendientes(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer dias,
            @RequestParam(required = false) Long especieId,
            @RequestParam(required = false) TipoAplicacion tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pendientes.pendientes(desde, hasta, dias, especieId, tipo, estado,
                PageRequests.of(page, size, Sort.unsorted()));
    }
}
