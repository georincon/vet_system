package com.veterinaria.laboratorio.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.veterinaria.laboratorio.api.dto.LabDtos.CapturaRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.CorreccionRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.EstadoOrdenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformeImagenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformePatologiaRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.ItemResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.MuestraRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenResumen;
import com.veterinaria.laboratorio.api.dto.LabDtos.ValidacionRequest;
import com.veterinaria.laboratorio.application.InformeItemService;
import com.veterinaria.laboratorio.application.InformePdfService;
import com.veterinaria.laboratorio.application.InformePdfService.Informe;
import com.veterinaria.laboratorio.application.OrdenService;
import com.veterinaria.laboratorio.application.ResultadoService;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/laboratorio/ordenes")
@Tag(name = "Laboratorio - ordenes", description = "Ordenes, bandeja, resultados, validacion, informes de imagen/patologia y PDF")
public class OrdenLabController {

    private final OrdenService ordenes;
    private final ResultadoService resultados;
    private final InformeItemService informes;
    private final InformePdfService pdf;

    public OrdenLabController(OrdenService ordenes, ResultadoService resultados, InformeItemService informes, InformePdfService pdf) {
        this.ordenes = ordenes;
        this.resultados = resultados;
        this.informes = informes;
        this.pdf = pdf;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una orden de examenes (ADMIN, VETERINARIO)")
    public OrdenResponse crear(@Valid @RequestBody OrdenRequest request) {
        return ordenes.crear(request);
    }

    @GetMapping
    @Operation(summary = "Bandeja de laboratorio: abiertas primero por prioridad (STAT, URGENTE, RUTINA) y antiguedad")
    public PageResponse<OrdenResumen> listar(@RequestParam(required = false) String estado,
            @RequestParam(required = false) String prioridad, @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long consultaId, @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ordenes.listar(estado, prioridad, pacienteId, consultaId, tipo, desde, hasta, q,
                PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de la orden: items, resultados vigentes y corregidos, informes, archivos y bitacora")
    public OrdenResponse obtener(@PathVariable Long id) {
        return ordenes.obtener(id);
    }

    @PostMapping("/{id}/muestra")
    @Operation(summary = "Registra la toma de muestra (ADMIN, VETERINARIO, AUXILIAR): SOLICITADA -> MUESTRA_TOMADA")
    public OrdenResponse muestra(@PathVariable Long id, @Valid @RequestBody(required = false) MuestraRequest request) {
        return ordenes.registrarMuestra(id, request);
    }

    @PostMapping("/{id}/estado")
    @Operation(summary = "Cambia el estado: EN_PROCESO o CANCELADA con motivo (ADMIN, VETERINARIO). COMPLETADA es automatica")
    public OrdenResponse estado(@PathVariable Long id, @Valid @RequestBody EstadoOrdenRequest request) {
        return ordenes.cambiarEstado(id, request);
    }

    @PutMapping("/{id}/items/{itemId}/resultados")
    @Operation(summary = "Captura/actualiza resultados (borrador) con bandera automatica (ADMIN, VETERINARIO, AUXILIAR)")
    public ItemResponse capturar(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody CapturaRequest request) {
        return resultados.capturar(id, itemId, request);
    }

    @PostMapping("/{id}/items/{itemId}/validar")
    @Operation(summary = "Valida el examen (inmutable). Con valores criticos la interpretacion es obligatoria (ADMIN, VETERINARIO)")
    public ItemResponse validar(@PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody(required = false) ValidacionRequest request) {
        return resultados.validar(id, itemId, request);
    }

    @PostMapping("/{id}/items/{itemId}/correccion")
    @Operation(summary = "Correccion enlazada de un examen validado: conserva el original como CORREGIDO (ADMIN, VETERINARIO)")
    public ItemResponse corregir(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody CorreccionRequest request) {
        return resultados.corregir(id, itemId, request);
    }

    @PutMapping("/{id}/items/{itemId}/informe-imagen")
    @Operation(summary = "Informe estructurado de imagen/ecografia (borrador; ADMIN, VETERINARIO)")
    public ItemResponse informeImagen(@PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody InformeImagenRequest request) {
        return informes.guardarImagen(id, itemId, request);
    }

    @PutMapping("/{id}/items/{itemId}/informe-patologia")
    @Operation(summary = "Informe de patologia/citologia (borrador; ADMIN, VETERINARIO)")
    public ItemResponse informePatologia(@PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody InformePatologiaRequest request) {
        return informes.guardarPatologia(id, itemId, request);
    }

    @GetMapping("/{id}/informe.pdf")
    @Operation(summary = "Informe PDF de la orden (o de un solo examen con itemId); los no validados salen como PRELIMINAR")
    public ResponseEntity<byte[]> informe(@PathVariable Long id, @RequestParam(required = false) Long itemId) {
        Informe informe = pdf.generar(id, itemId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("informe-" + informe.codigo() + ".pdf", StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(informe.pdf());
    }
}
