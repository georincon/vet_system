package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FormulaPlanApiTest extends EtapaBTestSupport {

    private byte[] pdf(String url, String bearer) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", bearer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void formulaPdfEsValidaYTraeDosisYFirma() throws Exception {
        Usuario vet = vetConTarjeta("TP-45678");
        CasoB c = casoB(true, true, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        PUT("/api/perfil/profesional", vet.bearer(), Map.of("especialidad", "Cirugia")).andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroProfesional").value("TP-45678"));

        byte[] doc = pdf("/api/consultas/" + q.consultaId() + "/formula.pdf", vet.bearer());
        assertTrue(new String(doc, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        String texto = textoPdf(doc);
        assertTrue(texto.contains("Formula medica"), texto);
        assertTrue(texto.contains("Amoxicilina") && texto.contains("1 tableta") && texto.contains("250 mg"), texto);
        assertTrue(texto.contains("cada 12 h") && texto.contains("7 dias"), texto);
        assertTrue(texto.contains("Gastroenteritis aguda"), texto);
        assertTrue(texto.contains("TP-45678"), texto);
        assertTrue(texto.contains("FOR-"), texto);
        assertTrue(texto.contains("Codigo de verificacion"), texto);

        // por receta: mismo numero y codigo (estable)
        String otro = textoPdf(pdf("/api/recetas/" + q.recetaId() + "/formula.pdf", vet.bearer()));
        String numero = jdbc.sql("select numero_formula from receta where id = :id").param("id", q.recetaId()).query(String.class).single();
        assertTrue(otro.contains(numero));

        // roles: auxiliar lee, recepcion no
        mockMvc.perform(get("/api/recetas/" + q.recetaId() + "/formula.pdf").header("Authorization", usuario("AUXILIAR").bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/recetas/" + q.recetaId() + "/formula.pdf").header("Authorization", usuario("RECEPCION").bearer()))
                .andExpect(status().isForbidden());
        GET("/api/recetas/999999/formula.pdf", vet.bearer()).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/recetas/" + q.recetaId() + "/formula.pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void planDeTratamientoYControlesRespetanLaInmutabilidad() throws Exception {
        Usuario vet = vetConTarjeta("TP-1");
        CasoB c = casoB(true, false, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), false);
        String base = "/api/consultas/" + q.consultaId() + "/plan-tratamiento";
        GET(base, vet.bearer()).andExpect(status().isNotFound());

        POST(base, vet.bearer(), mapa("objetivo", "Resolver el cuadro digestivo", "recomendaciones", "Reposo y agua fresca",
                "advertencias", "Suspender y consultar si hay sangre en el vomito",
                "items", List.of(
                        mapa("fase", "Fase 1", "tipo", "DIETA", "descripcion", "Dieta blanda 3 dias", "fechaInicio", hoy().toString(),
                                "fechaFin", hoy().plusDays(3).toString()),
                        mapa("fase", "Fase 2", "tipo", "CUIDADO", "descripcion", "Reintroducir alimento habitual"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.consultaCerrada").value(false));
        POST(base, vet.bearer(), Map.of()).andExpect(status().isConflict());
        POST(base, vet.bearer(), mapa("items", List.of(mapa("descripcion", "x", "fechaInicio", "2026-10-10", "fechaFin", "2026-10-01"))))
                .andExpect(status().isConflict());

        PUT(base, vet.bearer(), Map.of("objetivo", "Objetivo ajustado")).andExpect(status().isOk())
                .andExpect(jsonPath("$.objetivo").value("Objetivo ajustado")).andExpect(jsonPath("$.items", hasSize(2)));
        PUT(base, vet.bearer(), Map.of("items", List.of(mapa("descripcion", "Unica fase", "tipo", "MEDICACION"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
        // un auxiliar no escribe
        PUT(base, usuario("AUXILIAR").bearer(), Map.of("objetivo", "x")).andExpect(status().isForbidden());

        // controles: fecha anterior a la consulta -> 422; valido -> 201 con cita sugerida
        POST(base + "/controles", vet.bearer(), Map.of("fechaSugerida", hoy().minusDays(3).toString(), "motivo", "Control"))
                .andExpect(status().isUnprocessableEntity());
        POST(base + "/controles", vet.bearer(), Map.of("fechaSugerida", hoy().plusDays(7).toString())).andExpect(status().isUnprocessableEntity());
        long control = idDe(POST(base + "/controles", vet.bearer(), Map.of("fechaSugerida", hoy().plusDays(7).toString(),
                "motivo", "Control de hidratacion")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.citaSugerida.fecha").value(hoy().plusDays(7).toString()))
                .andExpect(jsonPath("$.citaSugerida.veterinarioId").value(vet.id())));

        // PDF de la formula incluye plan y controles
        String texto = textoPdf(pdf("/api/consultas/" + q.consultaId() + "/formula.pdf", vet.bearer()));
        assertTrue(texto.contains("Reposo y agua fresca") && texto.contains("Control de hidratacion"), texto);
        assertTrue(texto.contains("BORRADOR"), texto);

        // cierre: el plan queda inmutable (409) y tambien en BD
        POST("/api/consultas/" + q.consultaId() + "/cerrar", vet.bearer(), Map.of()).andExpect(status().isOk());
        PUT(base, vet.bearer(), Map.of("objetivo", "Cambio tardio")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("CERRADA")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(base).header("Authorization", vet.bearer()))
                .andExpect(status().isConflict());
        GET(base, vet.bearer()).andExpect(status().isOk()).andExpect(jsonPath("$.consultaCerrada").value(true))
                .andExpect(jsonPath("$.objetivo").value("Objetivo ajustado"));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> jdbc.sql("update plan_tratamiento set objetivo = 'hack' where consulta_id = :c").param("c", q.consultaId()).update());

        // el seguimiento si puede agregarse tras el cierre y deja un addendum SEGUIMIENTO
        POST(base + "/controles", vet.bearer(), Map.of("fechaSugerida", hoy().plusDays(30).toString(), "motivo", "Control mensual"))
                .andExpect(status().isCreated());
        GET("/api/consultas/" + q.consultaId() + "/addendums", vet.bearer()).andExpect(jsonPath("$[0].tipo").value("SEGUIMIENTO"));
        PUT(base + "/controles/" + control, vet.bearer(), Map.of("estado", "REALIZADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REALIZADO"));
        mockMvcDelete(base + "/controles/" + control, vet.bearer()).andExpect(status().isConflict());
        GET(base + "/controles", vet.bearer()).andExpect(jsonPath("$", hasSize(2)));
        // la formula ya cerrada deja de ser borrador
        assertTrue(!textoPdf(pdf("/api/consultas/" + q.consultaId() + "/formula.pdf", vet.bearer())).contains("BORRADOR"));
    }

    private org.springframework.test.web.servlet.ResultActions mockMvcDelete(String url, String bearer) throws Exception {
        return DELETE(url, bearer);
    }

    @Test
    void verificacionPublicaNoExponeDatosClinicos() throws Exception {
        Usuario vet = vetConTarjeta("TP-99001");
        CasoB c = casoB(true, true, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), false);
        pdf("/api/recetas/" + q.recetaId() + "/formula.pdf", vet.bearer());
        String codigo = jdbc.sql("select codigo_verificacion from receta where id = :id").param("id", q.recetaId()).query(String.class).single();
        assertEquals(10, codigo.length());
        String numero = jdbc.sql("select numero_formula from receta where id = :id").param("id", q.recetaId()).query(String.class).single();

        // sin autenticacion; mientras la consulta esta abierta es BORRADOR
        String r = mockMvc.perform(get("/api/verificar/" + codigo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true)).andExpect(jsonPath("$.numero").value(numero))
                .andExpect(jsonPath("$.estado").value("BORRADOR")).andExpect(jsonPath("$.tarjetaProfesional").value("TP-99001"))
                .andExpect(jsonPath("$.clinica").exists()).andExpect(jsonPath("$.fecha").value(hoy().toString()))
                .andReturn().getResponse().getContentAsString();
        assertTrue(!r.contains("Amoxicilina") && !r.contains("Mascota") && !r.contains("Gastroenteritis"), r);

        POST("/api/consultas/" + q.consultaId() + "/cerrar", vet.bearer(), Map.of()).andExpect(status().isOk());
        String conGuion = codigo.substring(0, 5) + "-" + codigo.substring(5).toLowerCase();
        mockMvc.perform(get("/api/verificar/" + conGuion)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("VIGENTE"));
        mockMvc.perform(get("/api/verificar/ZZZZZZZZZZ")).andExpect(status().isNotFound()).andExpect(jsonPath("$.valido").value(false));
        mockMvc.perform(get("/api/verificar/corto")).andExpect(status().isNotFound());
    }

    @Test
    void controlPuedeGenerarRecordatorioSoloConConsentimiento() throws Exception {
        Usuario vet = vetConTarjeta("TP-2");
        CasoB con = casoB(true, false, true);
        ConsultaB q = consultaConFormula(vet, con.pacienteId(), false);
        String fecha = hoy().plusDays(10).toString();
        POST("/api/consultas/" + q.consultaId() + "/plan-tratamiento/controles", vet.bearer(),
                Map.of("fechaSugerida", fecha, "motivo", "Revision", "generarRecordatorio", true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.recordatorio.solicitado").value(true))
                .andExpect(jsonPath("$.recordatorio.generado").value(true)).andExpect(jsonPath("$.recordatorio.recordatorioId").isNumber());
        GET("/api/comunicaciones/recordatorios?tipo=CONTROL&pacienteId=" + con.pacienteId(), admin())
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].estado").value("PENDIENTE"));

        CasoB sin = casoB(false, false, true);
        ConsultaB q2 = consultaConFormula(vet, sin.pacienteId(), false);
        POST("/api/consultas/" + q2.consultaId() + "/plan-tratamiento/controles", vet.bearer(),
                Map.of("fechaSugerida", fecha, "motivo", "Revision", "generarRecordatorio", true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.recordatorio.generado").value(false))
                .andExpect(jsonPath("$.recordatorio.detalle").value(containsString("consentimiento")));
        assertEquals(LocalDate.now().getClass(), LocalDate.class);
    }
}
