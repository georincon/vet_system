package com.veterinaria.compartir.api;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.compartir.api.dto.CompartirDtos.CompartirRequest;
import com.veterinaria.compartir.api.dto.CompartirDtos.EnvioResponse;
import com.veterinaria.compartir.api.dto.CompartirDtos.Previsualizacion;
import com.veterinaria.compartir.api.dto.CompartirDtos.RevocarRequest;
import com.veterinaria.compartir.application.CompartirService;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compartir")
@Tag(name = "Compartir con el tutor", description = "Envio de documentos clinicos por correo o WhatsApp (ADMIN, VETERINARIO)")
public class CompartirController {

    private final CompartirService service;

    public CompartirController(CompartirService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comparte documentos validados con el tutor (EMAIL con adjuntos; WHATSAPP con enlaces seguros)")
    public EnvioResponse compartir(@Valid @RequestBody CompartirRequest request) {
        return service.enviar(request);
    }

    @PostMapping("/previsualizar")
    @Operation(summary = "Muestra destinatario enmascarado, consentimientos faltantes y texto, sin enviar")
    public Previsualizacion previsualizar(@Valid @RequestBody CompartirRequest request) {
        return service.previsualizar(request);
    }

    @GetMapping("/historial")
    @Operation(summary = "Bitacora de envios (opcionalmente por paciente), mas reciente primero")
    public PageResponse<EnvioResponse> historial(@RequestParam(required = false) Long pacienteId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.historial(pacienteId, PageRequests.of(page, size, Sort.by("id")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un envio")
    public EnvioResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping("/{id}/reenviar")
    @Operation(summary = "Reenvia (genera enlaces nuevos y revoca los anteriores)")
    public EnvioResponse reenviar(@PathVariable Long id) {
        return service.reenviar(id);
    }

    @PostMapping("/{id}/revocar")
    @Operation(summary = "Revoca los enlaces del envio (solo ADMIN, VETERINARIO)")
    public EnvioResponse revocar(@PathVariable Long id, @Valid @RequestBody(required = false) RevocarRequest request) {
        return service.revocar(id, request == null ? null : request.motivo());
    }
}
