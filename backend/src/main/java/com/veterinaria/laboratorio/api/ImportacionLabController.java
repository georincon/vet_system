package com.veterinaria.laboratorio.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.veterinaria.laboratorio.api.dto.LabDtos.ImportacionJson;
import com.veterinaria.laboratorio.api.dto.LabDtos.ImportacionRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.ImportacionResponse;
import com.veterinaria.laboratorio.application.ImportacionService;
import com.veterinaria.laboratorio.application.ImportacionService.Comando;
import com.veterinaria.shared.error.BusinessException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/laboratorio")
@Tag(name = "Laboratorio - importacion", description = "HL7 v2 ORU^R01, CSV y JSON; endpoints de integracion con API key")
public class ImportacionLabController {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final ImportacionService service;
    private final JsonMapper mapper;

    public ImportacionLabController(ImportacionService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/importar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Importa resultados (JSON con contenido HL7/CSV como texto, o con 'resultados'). dryRun=true simula (ADMIN, VETERINARIO, AUXILIAR)")
    public ImportacionResponse importarJson(@Valid @RequestBody ImportacionRequest r) {
        return service.importar(comando(r.formato(), r.contenido(), r.resultados(), r.ordenId(), r.ordenCodigo(),
                Boolean.TRUE.equals(r.dryRun()), r.origen() == null ? "ARCHIVO" : r.origen(), null));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa un archivo HL7 (.hl7/.txt), CSV o JSON (parte 'archivo'). dryRun=true simula")
    public ImportacionResponse importarArchivo(@RequestParam String formato, @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(required = false) Long ordenId, @RequestParam(required = false) String ordenCodigo,
            @RequestParam(defaultValue = "false") boolean dryRun, @RequestParam(defaultValue = "ARCHIVO") String origen) {
        if (archivo.isEmpty()) {
            throw new BusinessException("El archivo esta vacio");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo de importacion supera 2 MB");
        }
        if (!java.util.List.of("HL7", "CSV", "JSON").contains(formato) || !java.util.List.of("ARCHIVO", "INTEGRACION").contains(origen)) {
            throw new BusinessException("formato (HL7, CSV, JSON) u origen (ARCHIVO, INTEGRACION) no validos");
        }
        try {
            return service.importar(comando(formato, decodificar(archivo.getBytes()), null, ordenId, ordenCodigo, dryRun, origen,
                    archivo.getOriginalFilename()));
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo");
        }
    }

    @GetMapping("/plantilla-csv")
    @Operation(summary = "Plantilla CSV descargable para importar resultados")
    public ResponseEntity<byte[]> plantilla() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("plantilla-resultados-laboratorio.csv", StandardCharsets.UTF_8).build().toString())
                .body(service.plantillaCsv().getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ integracion (analizadores/LIS, X-API-Key)

    @PostMapping(value = "/integracion/hl7")
    @Operation(summary = "Recibe un mensaje HL7 ORU^R01 (text/plain) de un analizador/LIS. Requiere cabecera X-API-Key")
    public ImportacionResponse integracionHl7(@RequestBody String mensaje, @RequestParam(required = false) Long ordenId,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return service.importar(new Comando("HL7", mensaje, null, ordenId, null, dryRun, "INTEGRACION", "integracion-hl7"));
    }

    @PostMapping("/integracion/resultados")
    @Operation(summary = "Recibe resultados en JSON de un analizador/LIS. Requiere cabecera X-API-Key")
    public ImportacionResponse integracionJson(@Valid @RequestBody ImportacionJson body,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return service.importar(new Comando("JSON", null, body.resultados(), body.ordenId(), body.ordenCodigo(), dryRun,
                "INTEGRACION", "integracion-json"));
    }

    // ------------------------------------------------------------------ utilidades

    private Comando comando(String formato, String contenido, java.util.List<com.veterinaria.laboratorio.api.dto.LabDtos.LineaJson> resultados,
            Long ordenId, String ordenCodigo, boolean dryRun, String origen, String nombre) {
        if ("JSON".equals(formato) && resultados == null && contenido != null) {
            try {
                ImportacionJson j = mapper.readValue(contenido, ImportacionJson.class);
                return new Comando(formato, null, j.resultados(), ordenId != null ? ordenId : j.ordenId(),
                        ordenCodigo != null ? ordenCodigo : j.ordenCodigo(), dryRun, origen, nombre);
            } catch (JacksonException e) {
                throw new BusinessException("El JSON de resultados no es valido: " + e.getOriginalMessage());
            }
        }
        return new Comando(formato, contenido, resultados, ordenId, ordenCodigo, dryRun, origen, nombre);
    }

    /** UTF-8 estricto; si no es valido (analizadores antiguos) se interpreta como ISO-8859-1. */
    private static String decodificar(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }
}
