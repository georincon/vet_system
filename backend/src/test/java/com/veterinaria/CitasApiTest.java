package com.veterinaria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CitasApiTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    String bearer;

    @BeforeEach
    void setup() throws Exception {
        bearer = "Bearer " + AuthApiTest.login(mockMvc);
    }

    @Test
    void crearCitaYListar() throws Exception {
        String especies = mockMvc.perform(get("/api/catalogos/especies").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        long perroId = idPorNombre(especies, "Perro");
        var razas = mockMvc.perform(get("/api/catalogos/razas?especieId=" + perroId).header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        String jsonRaza = razas.substring(1, razas.lastIndexOf("}"));
        long razaId = Long.parseLong(entre(jsonRaza, "\"id\":", ","));

        String tutorRes = mockMvc.perform(post("/api/tutores").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"9990001112\",\"nombres\":\"Sofia\",\"apellidos\":\"Mora\",\"activo\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tutorId = Long.parseLong(entre(tutorRes, "\"id\":", ","));

        String pacRes = mockMvc.perform(post("/api/pacientes").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nala\",\"especieId\":" + perroId + ",\"razaId\":" + razaId
                        + ",\"sexo\":\"H\"}"
                        .replace("\"sexo\":\"H\"", "\"sexo\":\"F\"")
                        .replace("\"sexo\":\"F\"", "\"sexo\":\"F\""))) // placeholder
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // fix sexo F
        String pacBody = "{\"nombre\":\"Nala\",\"especieId\":" + perroId + ",\"razaId\":" + razaId
                + ",\"sexo\":\"F\",\"tutorIds\":[" + tutorId + "],\"tutorPrincipalId\":" + tutorId + "}";
        pacRes = mockMvc.perform(post("/api/pacientes").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(pacBody))
                .andReturn().getResponse().getContentAsString();
        long pacienteId = Long.parseLong(entre(pacRes, "\"id\":", ","));

        // crear cita con veterinario admin? admin es ADMIN; requiere VETERINARIO? en regla: "El usuario seleccionado no es un veterinario" -> usamos admin? no, admin rol ADMIN. busquemos veterinario? no hay seed vet; pero podemos crear usuario VETERINARIO? o usar id 1? revisar: seed admin rol ADMIN -> no sirve. crear usuario vet
        String vetRes = mockMvc.perform(post("/api/usuarios").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"vet01\",\"password\":\"secret123\",\"nombre\":\"Vet Uno\",\"rol\":\"VETERINARIO\"}"))
                .andReturn().getResponse().getContentAsString();
        long vetId = Long.parseLong(entre(vetRes, "\"id\":", ","));

        String citaBody = "{\"pacienteId\":" + pacienteId + ",\"veterinarioId\":" + vetId
                + ",\"fecha\":\"" + LocalDate.now().plusDays(1) + "\""
                + ",\"horaInicio\":\"" + LocalTime.of(9, 0) + "\""
                + ",\"motivo\":\"Control\"}";
        String citaRes = mockMvc.perform(post("/api/citas").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(citaBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").exists())
                .andReturn().getResponse().getContentAsString();
        long citaId = Long.parseLong(entre(citaRes, "\"id\":", ","));

        mockMvc.perform(get("/api/citas").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(put("/api/citas/" + citaId).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notas\":\"Nota actualizada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notas").value("Nota actualizada"));
    }

    static String entre(String s, String ini, String fin) {
        int i = s.indexOf(ini);
        if (i < 0)
            throw new IllegalStateException("no ini: " + s);
        int start = i + ini.length();
        int j = s.indexOf(fin, start);
        if (j < 0)
            return s.substring(start);
        return s.substring(start, j);
    }

    static long idPorNombre(String json, String nombre) {
        int ini = json.indexOf("\"nombre\":\"" + nombre + "\"");
        if (ini < 0)
            throw new IllegalStateException("No se encontro '" + nombre + "' en: " + json);
        String trozo = json.substring(0, ini);
        int pos = trozo.lastIndexOf("\"id\":");
        if (pos < 0)
            throw new IllegalStateException("No hay id antes de '" + nombre + "' en: " + json);
        int start = pos + "\"id\":".length();
        int fin = trozo.indexOf(',', start);
        return Long.parseLong(fin < 0 ? trozo.substring(start) : trozo.substring(start, fin));
    }
}
