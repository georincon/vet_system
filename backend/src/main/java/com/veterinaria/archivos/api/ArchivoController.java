package com.veterinaria.archivos.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.veterinaria.archivos.ArchivosApi.ArchivoInfo;
import com.veterinaria.archivos.TipoArchivo;
import com.veterinaria.archivos.application.ArchivoService;
import com.veterinaria.archivos.application.ArchivoService.Descarga;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Archivos clinicos", description = "Imagenes, radiografias, ecografias, DICOM, PDF y consentimientos")
public class ArchivoController {

    private final ArchivoService service;

    public ArchivoController(ArchivoService service) {
        this.service = service;
    }

    @PostMapping(value = "/archivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sube un archivo clinico (ADMIN, VETERINARIO, AUXILIAR). Parte 'archivo' + campos del formulario")
    public ArchivoInfo subir(@RequestParam Long pacienteId,
            @RequestParam(required = false) Long consultaId,
            @RequestParam(required = false) Long ordenExamenId,
            @RequestParam(required = false) Long ordenItemId,
            @RequestParam(required = false) Long cirugiaId,
            @RequestParam TipoArchivo tipo,
            @RequestParam(required = false) String descripcion,
            @RequestParam("archivo") MultipartFile archivo) {
        return service.subir(pacienteId, consultaId, ordenExamenId, ordenItemId, cirugiaId, tipo, descripcion, archivo);
    }

    @GetMapping("/archivos")
    @Operation(summary = "Archivos activos por vinculo (al menos un filtro: consulta, orden, item o cirugia)")
    public List<ArchivoInfo> filtrar(@RequestParam(required = false) Long consultaId,
            @RequestParam(required = false) Long ordenExamenId, @RequestParam(required = false) Long ordenItemId,
            @RequestParam(required = false) Long cirugiaId) {
        return service.filtrar(consultaId, ordenExamenId, ordenItemId, cirugiaId);
    }

    @GetMapping("/archivos/{id}")
    @Operation(summary = "Metadatos de un archivo")
    public ArchivoInfo obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/archivos/{id}/contenido")
    @Operation(summary = "Descarga autenticada del contenido (Content-Type real; nosniff; DICOM como adjunto)")
    public ResponseEntity<Resource> contenido(@PathVariable Long id) {
        return respuesta(service.descargar(id, false));
    }

    @GetMapping("/archivos/{id}/miniatura")
    @Operation(summary = "Miniatura JPEG de imagenes JPG/PNG")
    public ResponseEntity<Resource> miniatura(@PathVariable Long id) {
        return respuesta(service.descargar(id, true));
    }

    @DeleteMapping("/archivos/{id}")
    @Operation(summary = "Baja logica con motivo obligatorio (ADMIN, VETERINARIO); el archivo se conserva")
    public ArchivoInfo eliminar(@PathVariable Long id, @RequestParam String motivo) {
        return service.eliminar(id, motivo);
    }

    @GetMapping("/pacientes/{pacienteId}/archivos")
    @Operation(summary = "Archivos del paciente (mas reciente primero); incluirEliminados muestra tambien las bajas")
    public List<ArchivoInfo> delPaciente(@PathVariable Long pacienteId,
            @RequestParam(required = false) TipoArchivo tipo,
            @RequestParam(defaultValue = "false") boolean incluirEliminados) {
        return service.listarPaciente(pacienteId, tipo, incluirEliminados);
    }

    private static ResponseEntity<Resource> respuesta(Descarga d) {
        ContentDisposition disp = (d.descargaForzada() ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(d.nombre(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disp.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .contentType(MediaType.parseMediaType(d.contentType()))
                .contentLength(d.tamano())
                .body(new FileSystemResource(d.ruta()));
    }
}
