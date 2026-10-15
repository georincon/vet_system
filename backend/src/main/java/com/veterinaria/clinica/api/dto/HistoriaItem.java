package com.veterinaria.clinica.api.dto;

import java.time.LocalDate;

/**
 * Evento de la linea de tiempo del paciente.
 * {@code tipo}: CONSULTA, VACUNA, DESPARASITACION, PESO o ALERGIA (las consultas cerradas con vacunas aportan VACUNA via
 * inmunizacion, sin duplicar el renglon de la consulta); {@code referenciaId} es el id del registro de ese tipo.
 * Etapa A: LABORATORIO, IMAGEN y PATOLOGIA (solo examenes validados; {@code referenciaId} = id de la orden),
 * PROCEDIMIENTO y CIRUGIA (realizados o en curso; id del registro) y ARCHIVO (archivos sueltos; id del adjunto).
 */
public record HistoriaItem(
        String tipo,
        LocalDate fecha,
        Long referenciaId,
        String titulo,
        String detalle,
        String estado) {
}
