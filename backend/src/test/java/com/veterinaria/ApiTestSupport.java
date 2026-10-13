package com.veterinaria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/** Utilidades comunes para pruebas de API: usuarios por rol, pacientes y verbos con token. */
@AutoConfigureMockMvc
abstract class ApiTestSupport extends BaseIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper mapper;

    /** "Hoy" en la zona de la clinica (la misma que usa el backend). */
    protected static java.time.LocalDate hoy() {
        return java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota"));
    }

    protected String admin() throws Exception {
        return "Bearer " + AuthApiTest.login(mockMvc);
    }

    /** Crea un usuario del rol indicado (como admin) y devuelve {id, token}. */
    protected Usuario usuario(String rol) throws Exception {
        String username = rol.toLowerCase() + SEQ.incrementAndGet();
        String res = send(post("/api/usuarios"), admin(), Map.of("username", username, "password", "secret123",
                "nombre", "Usuario " + username, "rol", rol)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Usuario(id, "Bearer " + JsonPath.read(login, "$.token"));
    }

    protected record Usuario(long id, String bearer) {
    }

    /** Crea tutor y paciente (perro hembra) y devuelve el id del paciente. */
    protected long paciente() throws Exception {
        String admin = admin();
        int n = SEQ.incrementAndGet();
        String tutor = send(post("/api/tutores"), admin, Map.of("tipoDocumento", "CC", "numeroDocumento", "55" + n,
                "nombres", "Tutor" + n, "apellidos", "Prueba")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tutorId = ((Number) JsonPath.read(tutor, "$.id")).longValue();
        String especies = mockMvc.perform(get("/api/catalogos/especies").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        long perro = primerId(especies, "$[?(@.nombre=='Perro')].id");
        String pac = send(post("/api/pacientes"), admin, Map.of("nombre", "Mascota" + n, "especieId", perro,
                "sexo", "F", "tutorIds", java.util.List.of(tutorId), "tutorPrincipalId", tutorId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(pac, "$.id")).longValue();
    }

    /** Primer valor numerico de un JsonPath con filtro (que devuelve lista). */
    protected static long primerId(String json, String path) {
        java.util.List<Number> valores = JsonPath.read(json, path);
        return valores.get(0).longValue();
    }

    protected long idDe(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    protected String json(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    protected ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String bearer, Object body) throws Exception {
        return mockMvc.perform(builder.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    protected ResultActions POST(String url, String bearer, Object body) throws Exception {
        return send(post(url), bearer, body);
    }

    protected ResultActions PUT(String url, String bearer, Object body) throws Exception {
        return send(put(url), bearer, body);
    }

    protected ResultActions GET(String url, String bearer) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", bearer));
    }

    protected ResultActions DELETE(String url, String bearer) throws Exception {
        return mockMvc.perform(delete(url).header("Authorization", bearer));
    }

    /** Cita para el dia {@code dias} a partir de hoy; devuelve el id. */
    protected ResultActions crearCita(String bearer, long pacienteId, long vetId, int dias, String inicio,
            Map<String, Object> extra) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("pacienteId", pacienteId, "veterinarioId", vetId,
                "fecha", hoy().plusDays(dias).toString(), "horaInicio", inicio));
        body.putAll(extra);
        return POST("/api/citas", bearer, body);
    }
}
