package com.veterinaria.clinica.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.clinica.application.VerificacionDocumentoService;
import com.veterinaria.clinica.application.VerificacionDocumentoService.Verificacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Verificacion PUBLICA (sin autenticacion) de documentos por codigo; sin datos clinicos. */
@RestController
@RequestMapping("/api/verificar")
@Tag(name = "Verificacion de documentos", description = "Publico: confirma que una formula fue emitida por la clinica")
public class VerificacionController {

    private final VerificacionDocumentoService service;

    public VerificacionController(VerificacionDocumentoService service) {
        this.service = service;
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Valida un codigo de verificacion; 404 con valido=false si no existe",
            description = "Responde solo existencia, fecha, clinica, veterinario (con tarjeta profesional) y estado.")
    public ResponseEntity<Verificacion> verificar(@PathVariable String codigo) {
        return service.verificar(codigo).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Verificacion.invalido()));
    }
}
