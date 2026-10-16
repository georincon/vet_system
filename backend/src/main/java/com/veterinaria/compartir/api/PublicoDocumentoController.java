package com.veterinaria.compartir.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.compartir.application.EnlaceService;
import com.veterinaria.compartir.application.EnlaceService.Descarga;
import com.veterinaria.shared.error.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/** Descarga PUBLICA (sin autenticacion) de un documento compartido, por token. */
@RestController
@RequestMapping("/api/publico/documentos")
@Tag(name = "Enlaces publicos", description = "Descarga por token (sin sesion)")
public class PublicoDocumentoController {

    private final EnlaceService service;

    public PublicoDocumentoController(EnlaceService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Descarga el PDF; 404 token desconocido, 410 revocado/vencido/limite de descargas")
    public ResponseEntity<?> descargar(@PathVariable String token, HttpServletRequest request) {
        Descarga d = service.descargar(token, request.getRemoteAddr(), request.getHeader("User-Agent"));
        return switch (d.estado()) {
            case OK -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(d.nombre(), StandardCharsets.UTF_8).build().toString())
                    .header("Cache-Control", "no-store, private").header("X-Content-Type-Options", "nosniff")
                    .header("X-Robots-Tag", "noindex, nofollow").body(d.pdf());
            case NO_ENCONTRADO -> error(HttpStatus.NOT_FOUND, "Enlace no encontrado", request);
            default -> error(HttpStatus.GONE, "El enlace ya no esta disponible (vencido, revocado o con el limite de descargas alcanzado)", request);
        };
    }

    private static ResponseEntity<ApiError> error(HttpStatus s, String msg, HttpServletRequest r) {
        return ResponseEntity.status(s).contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(s.value(), s.getReasonPhrase(), msg, "/api/publico/documentos"));
    }
}
