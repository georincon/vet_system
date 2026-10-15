package com.veterinaria.comunicaciones.api.dto;

import java.time.Instant;

/** Bitacora de envios: un registro por intento (exitoso, simulado o fallido). */
public record EnvioLogResponse(
        Long id,
        Long recordatorioId,
        Long tutorId,
        String canal,
        String destinatario,
        String asunto,
        String cuerpo,
        String estado,
        String proveedor,
        String proveedorMensajeId,
        String error,
        Instant enviadoEn) {
}
