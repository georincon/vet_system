package com.veterinaria.clinica.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.clinica.application.FormulaService;
import com.veterinaria.clinica.application.FormulaService.Formula;
import com.veterinaria.clinica.application.ResumenConsultaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** PDF de la formula medica (por consulta o por receta) y resumen de consulta. Roles: ADMIN, VETERINARIO, AUXILIAR (lectura). */
@RestController
@Tag(name = "Formula medica", description = "Formula medica en PDF con firma, tarjeta profesional y codigo de verificacion")
public class FormulaController {

    private final FormulaService formulas;
    private final ResumenConsultaService resumenes;

    public FormulaController(FormulaService formulas, ResumenConsultaService resumenes) {
        this.formulas = formulas;
        this.resumenes = resumenes;
    }

    @GetMapping(value = "/api/consultas/{consultaId}/formula.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Formula medica de la consulta (su receta vigente mas reciente) en PDF")
    public ResponseEntity<byte[]> formulaConsulta(@PathVariable Long consultaId) {
        return pdf(formulas.porConsulta(consultaId));
    }

    @GetMapping(value = "/api/recetas/{recetaId}/formula.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Formula medica de una receta en PDF")
    public ResponseEntity<byte[]> formulaReceta(@PathVariable Long recetaId) {
        return pdf(formulas.porReceta(recetaId));
    }

    @GetMapping(value = "/api/consultas/{consultaId}/resumen.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Resumen de la consulta en PDF (solo consultas CERRADAS; 422 si esta abierta)")
    public ResponseEntity<byte[]> resumen(@PathVariable Long consultaId) {
        var r = resumenes.generar(consultaId);
        return salida("resumen-" + r.codigo() + ".pdf", r.pdf());
    }

    private static ResponseEntity<byte[]> pdf(Formula f) {
        return salida(f.numero() + ".pdf", f.pdf());
    }

    private static ResponseEntity<byte[]> salida(String nombre, byte[] pdf) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(nombre, StandardCharsets.UTF_8).build().toString())
                .header("Cache-Control", "no-store, private").body(pdf);
    }
}
