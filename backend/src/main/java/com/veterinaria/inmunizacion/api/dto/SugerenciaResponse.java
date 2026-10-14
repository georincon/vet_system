package com.veterinaria.inmunizacion.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Que le toca al paciente segun especie, edad y aplicadas. {@code estado} por esquema: COMPLETA (serie terminada
 * sin refuerzo periodico), VENCIDA, TOCA_AHORA, PROXIMA (<= 30 dias), AL_DIA (ya vacunado, proxima lejana) o FUTURA
 * (aun no aplicada, fecha lejana).
 */
public record SugerenciaResponse(
        Long pacienteId,
        String paciente,
        String especie,
        LocalDate fechaNacimiento,
        Integer edadSemanas,
        List<Item> items) {

    public record Item(
            Long esquemaId,
            String esquema,
            Long vacunaId,
            String vacuna,
            String clasificacion,
            boolean obligatoriaLey,
            int dosisAplicadas,
            LocalDate ultimaAplicacion,
            Integer proximaDosisNumero,
            String proximaDosisNombre,
            LocalDate fechaSugerida,
            String estado) {
    }
}
