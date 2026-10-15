package com.veterinaria.comunicaciones.api.dto;

import java.time.Instant;

/**
 * Recordatorio de la cola. {@code estado}: PENDIENTE, ENVIADO, ENVIADO_SIMULADO (sin SMTP configurado), FALLIDO
 * (agoto reintentos), CANCELADO u OMITIDO_OPTOUT (el tutor revoco consentimiento antes del envio).
 */
public record RecordatorioResponse(
        Long id,
        Long reglaId,
        String regla,
        Long tutorId,
        String tutor,
        Long pacienteId,
        String paciente,
        String tipo,
        String referenciaTabla,
        Long referenciaId,
        String canal,
        Instant programadoPara,
        String estado,
        int intentos,
        String ultimoError,
        Instant enviadoEn,
        Instant creadoEn) {
}
