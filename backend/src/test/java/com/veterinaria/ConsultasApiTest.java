package com.veterinaria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ConsultasApiTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    String bearer;

    @BeforeEach
    void setup() throws Exception {
        bearer = "Bearer " + AuthApiTest.login(mockMvc);
    }

    @Test
    void crearConsultaYListar() throws Exception {
        String especies = mockMvc.perform(get("/api/catalogos/especies").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        long perroId = CitasApiTest.idPorNombre(especies, "Perro");
        var razas = mockMvc.perform(get("/api/catalogos/razas?especieId=" + perroId).header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        String jsonRaza = razas.substring(1, razas.lastIndexOf("}"));
        long razaId = Long.parseLong(CitasApiTest.entre(jsonRaza, "\"id\":", ","));

        String tutorRes = mockMvc.perform(post("/api/tutores").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"8887776665\",\"nombres\":\"Luis\",\"apellidos\":\"Rojas\",\"activo\":true}"))
                .andReturn().getResponse().getContentAsString();
        long tutorId = Long.parseLong(CitasApiTest.entre(tutorRes, "\"id\":", ","));

        String pacBody = "{\"nombre\":\"Kira\",\"especieId\":" + perroId + ",\"razaId\":" + razaId
                + ",\"sexo\":\"F\",\"tutorIds\":[" + tutorId + "],\"tutorPrincipalId\":" + tutorId + "}";
        String pacRes = mockMvc.perform(post("/api/pacientes").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(pacBody))
                .andReturn().getResponse().getContentAsString();
        long pacienteId = Long.parseLong(CitasApiTest.entre(pacRes, "\"id\":", ","));

        String vetRes = mockMvc.perform(post("/api/usuarios").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"vet02\",\"password\":\"secret123\",\"nombre\":\"Vet Dos\",\"rol\":\"VETERINARIO\"}"))
                .andReturn().getResponse().getContentAsString();
        long vetId = Long.parseLong(CitasApiTest.entre(vetRes, "\"id\":", ","));

        String consultaBody = "{\"pacienteId\":" + pacienteId + ",\"veterinarioId\":" + vetId
                + ",\"fecha\":\"" + LocalDate.now() + "\""
                + ",\"motivo\":\"Consulta general\",\"diagnostico\":\"Saludable\",\"pesoKg\":15.5}";
        mockMvc.perform(post("/api/consultas").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(consultaBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").exists())
                .andExpect(jsonPath("$.pesoKg").value(15.5));

        mockMvc.perform(get("/api/consultas").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }
}
