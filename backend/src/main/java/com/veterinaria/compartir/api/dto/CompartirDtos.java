package com.veterinaria.compartir.api.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CompartirDtos {

    private CompartirDtos() {
    }

    /** {@code id}: receta (FORMULA), orden (LABORATORIO/IMAGEN/PATOLOGIA), procedimiento, paciente (CARNET) o consulta (RESUMEN_CONSULTA). */
    public record DocumentoRef(
            @NotNull(message = "El tipo de documento es obligatorio")
            @Pattern(regexp = "FORMULA|LABORATORIO|IMAGEN|PATOLOGIA|PROCEDIMIENTO|CARNET|RESUMEN_CONSULTA", message = "Tipo de documento no valido") String tipo,
            @NotNull(message = "El id del documento es obligatorio") Long id,
            Long itemId) {
    }

    /** {@code mensaje}: nota opcional de la clinica que se agrega al texto (no escriba datos clinicos). */
    public record CompartirRequest(
            @NotNull(message = "El paciente es obligatorio") Long pacienteId,
            Long tutorId,
            @NotNull(message = "El canal es obligatorio")
            @Pattern(regexp = "EMAIL|WHATSAPP", message = "Canal no valido (EMAIL o WHATSAPP)") String canal,
            @NotEmpty(message = "Indique al menos un documento") List<@Valid DocumentoRef> documentos,
            @Size(max = 500, message = "El mensaje no puede exceder 500 caracteres") String mensaje,
            Long veterinarioResponsableId) {
    }

    public record RevocarRequest(@Size(max = 255, message = "El motivo no puede exceder 255 caracteres") String motivo) {
    }

    public record DocumentoEnvio(Long id, String tipo, Long documentoId, Long itemId, String titulo, String nombreArchivo,
            int tamanoBytes, String hashSha256) {
    }

    /** {@code url} solo viene en la respuesta del envio/reenvio (el token no se guarda ni se puede recuperar). */
    public record EnlaceInfo(Long id, Long documentoEnvioId, String tokenPrefijo, String url, Instant expiraEn,
            int maxDescargas, int descargas, boolean revocado, Instant ultimoAcceso) {
    }

    public record EnvioResponse(Long id, Long pacienteId, Long tutorId, String canal, String destinatario, String asunto,
            String mensaje, String estado, int intentos, String ultimoError, String proveedor, Long creadoPor,
            String creadoPorNombre, Long responsableId, Instant creadoEn, Instant enviadoEn, boolean revocado, Instant revocadoEn,
            List<DocumentoEnvio> documentos, List<EnlaceInfo> enlaces, String urlWaMe) {
    }

    public record DocumentoPrevio(String tipo, Long id, String titulo, boolean enviable, String problema) {
    }

    public record Previsualizacion(Long tutorId, String tutor, String canal, String destinatario, boolean consentimientoCanal,
            boolean autorizaTratamientoDatos, boolean bajaComunicaciones, boolean contactoValido, List<String> faltantes,
            boolean puedeEnviar, String asunto, String mensaje, List<DocumentoPrevio> documentos) {
    }
}
