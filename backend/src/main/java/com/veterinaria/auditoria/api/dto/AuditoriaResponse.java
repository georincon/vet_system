package com.veterinaria.auditoria.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * Registro de auditoria. {@code accion}: INSERT, UPDATE o DELETE; {@code entidad}: nombre de la tabla.
 * {@code detalle} es JSON: {"despues":{...}} en altas, {"antes":{...}} en bajas y {"antes":{..},"despues":{..}}
 * con solo las columnas modificadas en cambios.
 */
public record AuditoriaResponse(
        Long id,
        Long usuarioId,
        String usuario,
        String accion,
        String entidad,
        Long entidadId,
        @JsonRawValue String detalle,
        String ip,
        Instant ocurridoEn) {
}
