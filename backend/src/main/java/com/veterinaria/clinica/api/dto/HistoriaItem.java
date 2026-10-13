package com.veterinaria.clinica.api.dto;

import java.time.LocalDate;

/**
 * Evento de la linea de tiempo del paciente.
 * {@code tipo}: CONSULTA, VACUNA, PESO o ALERGIA; {@code referenciaId} es el id del registro de ese tipo.
 */
public record HistoriaItem(
        String tipo,
        LocalDate fecha,
        Long referenciaId,
        String titulo,
        String detalle,
        String estado) {
}
