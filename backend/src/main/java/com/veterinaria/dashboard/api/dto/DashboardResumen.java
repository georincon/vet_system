package com.veterinaria.dashboard.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Indicadores de la pantalla de inicio. {@code vacunasPorVencer}: proximas dosis en los siguientes 30 dias. */
public record DashboardResumen(
        long citasHoy,
        long citasPendientes,
        long pacientesActivos,
        long tutoresActivos,
        long consultasAbiertas,
        long consultasMes,
        long vacunasPorVencer,
        List<ProximaCita> proximasCitas) {

    public record ProximaCita(
            Long id,
            String codigo,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            Persona paciente,
            Persona veterinario,
            String estado,
            String motivo) {
    }

    public record Persona(Long id, String nombre) {
    }
}
