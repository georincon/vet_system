package com.veterinaria;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TutoresApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String token() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return entre(body, "\"token\":\"", "\"");
    }

    private static Long idDe(String body) {
        return Long.parseLong(entre(body, "\"id\":", ","));
    }

    private static String entre(String s, String desde, String hasta) {
        int ini = s.indexOf(desde);
        if (ini < 0) {
            throw new IllegalStateException("No se encontro '" + desde + "' en: " + s);
        }
        ini += desde.length();
        int fin = s.indexOf(hasta, ini);
        return fin < 0 ? s.substring(ini) : s.substring(ini, fin);
    }

    private static String idPorNombre(String json, String nombre) {
        int ini = json.indexOf("\"nombre\":\"" + nombre + "\"");
        if (ini < 0) {
            throw new IllegalStateException("No se encontro '" + nombre + "' en: " + json);
        }
        String trozo = json.substring(0, ini);
        int pos = trozo.lastIndexOf("\"id\":");
        if (pos < 0) {
            throw new IllegalStateException("No hay id antes de '" + nombre + "' en: " + json);
        }
        int start = pos + "\"id\":".length();
        int fin = trozo.indexOf(',', start);
        return fin < 0 ? trozo.substring(start) : trozo.substring(start, fin);
    }

    @Test
    void catalogoEspeciesSeSembraYConsulta() throws Exception {
        mockMvc.perform(get("/api/catalogos/especies").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[0].nombre").value("Ave"));
    }

    @Test
    void crearTutorYPacienteVinculaTutorPrincipal() throws Exception {
        String bearer = "Bearer " + token();

        MvcResult tutor = mockMvc.perform(post("/api/tutores")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoDocumento":"CC","numeroDocumento":"1000001","nombres":"Maria",
                                 "apellidos":"Perez","telefono":"3001234567","email":"maria@example.com",
                                 "ciudad":"Bogota"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        Long tutorId = idDe(tutor.getResponse().getContentAsString());

        mockMvc.perform(get("/api/tutores/" + tutorId + "/pacientes").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));

        String especiesBody = mockMvc.perform(get("/api/catalogos/especies").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        String perroId = idPorNombre(especiesBody, "Perro");

        String razasBody = mockMvc.perform(get("/api/catalogos/razas?especieId=" + perroId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String criolloId = idPorNombre(razasBody, "Criollo");

        mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"nombre":"Rex","especieId":%s,"razaId":%s,"sexo":"M",
                                 "fechaNacimiento":"2022-03-01","pesoActual":18.5,
                                 "tutorIds":[%s],"tutorPrincipalId":%s}
                                """).formatted(perroId, criolloId, tutorId, tutorId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo", startsWith("PAC-")))
                .andExpect(jsonPath("$.tutores", hasSize(1)))
                .andExpect(jsonPath("$.tutores[0].esPrincipal").value(true))
                .andExpect(jsonPath("$.tutores[0].nombres").value("Maria"));

        mockMvc.perform(get("/api/pacientes?q=Rex").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].nombre").value("Rex"));
    }

    @Test
    void catalogoRazasCaninasExponeDatosFci() throws Exception {
        String bearer = "Bearer " + token();
        String especiesBody = mockMvc.perform(get("/api/catalogos/especies").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        String perroId = idPorNombre(especiesBody, "Perro");

        String razas = mockMvc.perform(get("/api/catalogos/razas?especieId=" + perroId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.potencialmentePeligrosa == true)]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.grupoFci != null)]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(razas.contains("\"grupoFci\""));
        org.junit.jupiter.api.Assertions.assertTrue(razas.contains("\"potencialmentePeligrosa\":true"));
    }

    @Test
    void crearPacienteConRazaDeOtraEspecieDevuelve422() throws Exception {
        String bearer = "Bearer " + token();
        String especiesBody = mockMvc.perform(get("/api/catalogos/especies").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        String perroId = idPorNombre(especiesBody, "Perro");
        String gatoId = idPorNombre(especiesBody, "Gato");

        String razasGato = mockMvc.perform(get("/api/catalogos/razas?especieId=" + gatoId)
                        .header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        String razaGatoId = idPorNombre(razasGato, "Criollo");

        mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"nombre":"Mich","especieId":%s,"razaId":%s,"sexo":"F"}
                                """).formatted(perroId, razaGatoId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void actualizarTutorAplicaCambios() throws Exception {
        String bearer = "Bearer " + token();
        MvcResult creado = mockMvc.perform(post("/api/tutores")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoDocumento":"CC","numeroDocumento":"1000002","nombres":"Luis",
                                 "apellidos":"Gomez","telefono":"3009999999"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long tutorId = idDe(creado.getResponse().getContentAsString());

        mockMvc.perform(put("/api/tutores/" + tutorId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoDocumento":"CC","numeroDocumento":"1000002","nombres":"Luis",
                                 "apellidos":"Gomez","telefono":"3009999999","ciudad":"Medellin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudad").value("Medellin"));
    }

    @Test
    void duplicarDocumentoDevuelve409() throws Exception {
        String bearer = "Bearer " + token();
        String payload = """
                {"tipoDocumento":"CC","numeroDocumento":"777777","nombres":"Ana","apellidos":"Diaz"}
                """;
        mockMvc.perform(post("/api/tutores").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/tutores").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }
}