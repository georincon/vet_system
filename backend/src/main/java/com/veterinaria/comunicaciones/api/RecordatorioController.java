package com.veterinaria.comunicaciones.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.comunicaciones.api.dto.EjecucionResponse;
import com.veterinaria.comunicaciones.api.dto.EnvioLogResponse;
import com.veterinaria.comunicaciones.api.dto.OptOutRequest;
import com.veterinaria.comunicaciones.api.dto.OptOutResponse;
import com.veterinaria.comunicaciones.api.dto.RecordatorioResponse;
import com.veterinaria.comunicaciones.application.OptOutService;
import com.veterinaria.comunicaciones.application.RecordatorioService;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/comunicaciones")
@Tag(name = "Comunicaciones")
public class RecordatorioController {

    private final RecordatorioService recordatorios;
    private final OptOutService optOut;

    public RecordatorioController(RecordatorioService recordatorios, OptOutService optOut) {
        this.recordatorios = recordatorios;
        this.optOut = optOut;
    }

    @GetMapping("/recordatorios")
    @Operation(summary = "Lista recordatorios (filtros: estado, tipo, canal, tutorId, pacienteId, desde, hasta)")
    public PageResponse<RecordatorioResponse> listar(
            @RequestParam(required = false) String estado, @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String canal, @RequestParam(required = false) Long tutorId,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return recordatorios.listar(estado, tipo, canal, tutorId, pacienteId, desde, hasta,
                PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/recordatorios/{id}")
    public RecordatorioResponse obtener(@PathVariable Long id) {
        return recordatorios.obtener(id);
    }

    @GetMapping("/recordatorios/{id}/bitacora")
    @Operation(summary = "Intentos de envio (envio_log) de un recordatorio")
    public List<EnvioLogResponse> bitacora(@PathVariable Long id) {
        return recordatorios.bitacora(id);
    }

    @PostMapping("/recordatorios/{id}/cancelar")
    @Operation(summary = "Cancela un recordatorio PENDIENTE (409 si ya no lo esta)")
    public RecordatorioResponse cancelar(@PathVariable Long id) {
        return recordatorios.cancelar(id);
    }

    @GetMapping("/envios")
    @Operation(summary = "Bitacora global de envios (filtros: estado, tutorId)")
    public PageResponse<EnvioLogResponse> envios(@RequestParam(required = false) String estado,
            @RequestParam(required = false) Long tutorId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recordatorios.envios(estado, tutorId, PageRequests.of(page, size, Sort.unsorted()));
    }

    @PostMapping("/ejecutar")
    @Operation(summary = "Dispara el job manualmente: genera recordatorios y procesa la cola (solo ADMIN)")
    public EjecucionResponse ejecutar() {
        return recordatorios.ejecutar();
    }

    // ---- opt-out
    @GetMapping(value = "/optout", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Publico: baja de comunicaciones con el token firmado incluido en los correos")
    public ResponseEntity<String> optOutPorToken(@RequestParam String token) {
        boolean ok = optOut.darDeBajaPorToken(token).isPresent();
        String html = ok
                ? "<!doctype html><meta charset=utf-8><title>Baja registrada</title><body style=\"font-family:sans-serif;max-width:32rem;margin:3rem auto\"><h2>Listo</h2><p>No volvera a recibir recordatorios por este medio.</p></body>"
                : "<!doctype html><meta charset=utf-8><title>Enlace invalido</title><body style=\"font-family:sans-serif;max-width:32rem;margin:3rem auto\"><h2>Enlace invalido</h2><p>El enlace de baja no es valido.</p></body>";
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/optouts")
    @Operation(summary = "Bajas registradas (filtro opcional tutorId)")
    public List<OptOutResponse> optOuts(@RequestParam(required = false) Long tutorId) {
        return optOut.listar(tutorId);
    }

    @PostMapping("/optouts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una baja manual de un tutor")
    public OptOutResponse registrarOptOut(@Valid @RequestBody OptOutRequest r) {
        return optOut.registrar(r);
    }

    @DeleteMapping("/optouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Levanta una baja")
    public void eliminarOptOut(@PathVariable Long id) {
        optOut.eliminar(id);
    }
}
