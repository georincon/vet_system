package com.veterinaria.clinica;

import java.time.LocalDate;
import java.util.List;

/**
 * Evento de dominio publicado al cerrar una consulta (API del modulo clinica). Lo consume
 * {@code inmunizacion} para convertir las vacunas de la consulta en inmunizaciones, sin que clinica
 * dependa de ese modulo y sin modificar la consulta ya cerrada (inmutable).
 */
public record ConsultaCerrada(
        Long consultaId,
        Long pacienteId,
        Long veterinarioId,
        LocalDate fecha,
        List<VacunaAplicada> vacunas) {

    /** Renglon {@code consulta_vacuna}; {@code id} identifica el origen (clave de idempotencia). */
    public record VacunaAplicada(Long id, String nombre, String lote, LocalDate fechaAplicacion, LocalDate proximaFecha) {
    }
}
