package com.veterinaria;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginAdminDevuelveToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}
                                """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.token", not(emptyString())))
               .andExpect(jsonPath("$.usuario.rol").value("ADMIN"))
               .andExpect(jsonPath("$.usuario.username").value("admin"));
    }

    @Test
    void loginConCredencialesInvalidasRechaza() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"incorrecta"}
                                """))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void accesoProtegidoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
               .andExpect(status().isUnauthorized());
    }

    static String login(MockMvc mockMvc) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int i = body.indexOf("token");
        if (i < 0) throw new IllegalStateException("token no encontrado: " + body);
        int q1 = body.indexOf('"', i);
        int q2 = body.indexOf(':', q1);
        int q3 = body.indexOf('"', q2 + 1);
        int q4 = body.indexOf('"', q3 + 1);
        return body.substring(q3 + 1, q4);
    }

}