package com.veterinaria;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DashboardApiTest extends ApiTestSupport {

    @Test
    void resumenReflejaLosDatosYEsAccesibleParaTodosLosRoles() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = paciente();
        crearCita(admin(), paciente, vet.id(), 0, "23:00", Map.of("horaFin", "23:30")).andExpect(status().isCreated());
        long otra = idDe(crearCita(admin(), paciente, vet.id(), 2, "09:00", Map.of()));
        POST("/api/citas/" + otra + "/estado", admin(), Map.of("estado", "CONFIRMADA")).andExpect(status().isOk());
        POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente, "veterinarioId", vet.id(),
                "fecha", hoy().toString(), "vacunas", java.util.List.of(Map.of("nombre", "Rabia",
                        "proximaFecha", hoy().plusDays(10).toString())))).andExpect(status().isCreated());

        GET("/api/dashboard/resumen", usuario("RECEPCION").bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.citasHoy").value(1))
                .andExpect(jsonPath("$.citasPendientes").value(2))
                .andExpect(jsonPath("$.pacientesActivos").value(1))
                .andExpect(jsonPath("$.tutoresActivos").value(1))
                .andExpect(jsonPath("$.consultasAbiertas").value(1))
                .andExpect(jsonPath("$.consultasMes").value(1))
                .andExpect(jsonPath("$.vacunasPorVencer").value(1))
                .andExpect(jsonPath("$.proximasCitas", hasSize(2)))
                .andExpect(jsonPath("$.proximasCitas[0].paciente.nombre").exists());
        GET("/api/dashboard/resumen", usuario("AUXILIAR").bearer()).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard/resumen"))
                .andExpect(status().isUnauthorized());
    }
}
