package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.jayway.jsonpath.JsonPath;
import com.veterinaria.comunicaciones.application.WhatsappFalso;

import tools.jackson.databind.json.JsonMapper;

/** Compartir por WhatsApp contra un servidor HTTP falso de la Cloud API, y AUXILIAR habilitado por configuracion. */
class CompartirWhatsappApiTest extends EtapaBTestSupport {

    private static final WhatsappFalso META = new WhatsappFalso();
    private static final JsonMapper M = JsonMapper.builder().build();

    @DynamicPropertySource
    static void whatsapp(DynamicPropertyRegistry r) {
        r.add("vet.whatsapp.base-url", META::baseUrl);
        r.add("vet.whatsapp.token", () -> "TOKEN-DE-PRUEBA");
        r.add("vet.whatsapp.phone-id", () -> "55501234");
        r.add("vet.whatsapp.template-name", () -> "documento_clinica");
        r.add("vet.whatsapp.template-params", () -> "tutor,paciente,enlace");
        r.add("vet.whatsapp.backoff-ms", () -> "1");
        r.add("vet.whatsapp.max-reintentos", () -> "1");
        r.add("vet.compartir.auxiliar-habilitado", () -> "true");
    }

    @AfterAll
    static void cerrar() {
        META.close();
    }

    @Test
    void enviaLaPlantillaConElEnlaceSeguroSinDatosClinicos() throws Exception {
        Usuario vet = vetConTarjeta("TP-20");
        CasoB c = casoB(false, true, true, "310 555 0199");
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        int antes = META.peticiones.size();
        String r = json(POST("/api/compartir", vet.bearer(), mapa("pacienteId", c.pacienteId(), "canal", "WHATSAPP",
                "documentos", List.of(mapa("tipo", "FORMULA", "id", q.recetaId()), mapa("tipo", "CARNET", "id", c.pacienteId()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("ENVIADO"))
                .andExpect(jsonPath("$.proveedor").value("WHATSAPP_CLOUD_API")).andExpect(jsonPath("$.proveedorMensajeId").doesNotExist()));
        // un mensaje de plantilla por documento
        assertEquals(2, META.peticiones.size() - antes);
        var p = META.peticiones.get(antes);
        assertEquals("/v21.0/55501234/messages", p.ruta());
        assertEquals("Bearer TOKEN-DE-PRUEBA", p.autorizacion());
        var j = M.readTree(p.cuerpo());
        assertEquals("573105550199", j.path("to").asString());
        assertEquals("template", j.path("type").asString());
        var ps = j.path("template").path("components").path(0).path("parameters");
        assertEquals(c.tutorNombre(), ps.path(0).path("text").asString());
        assertEquals("Mascota", ps.path(1).path("text").asString().replaceAll("\\d+", ""));
        String enlace = ps.path(2).path("text").asString();
        assertTrue(enlace.contains("/api/publico/documentos/"), enlace);
        assertFalse(p.cuerpo().contains("Amoxicilina") || p.cuerpo().contains("Gastroenteritis"));
        // el enlace enviado a Meta funciona sin sesion
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(enlace.substring(enlace.indexOf("/api/"))))
                .andExpect(status().isOk());
        // wa.me incluye los dos enlaces
        String wame = JsonPath.read(r, "$.urlWaMe");
        assertTrue(java.net.URLDecoder.decode(wame, java.nio.charset.StandardCharsets.UTF_8).split("/api/publico/documentos/").length == 3, wame);
    }

    @Test
    void fallaDeLaApiQuedaFallidaYSePuedeReenviar() throws Exception {
        Usuario vet = vetConTarjeta("TP-21");
        CasoB c = casoB(false, true, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        META.programar(401);
        String r = json(POST("/api/compartir", vet.bearer(), mapa("pacienteId", c.pacienteId(), "canal", "WHATSAPP",
                "documentos", List.of(mapa("tipo", "FORMULA", "id", q.recetaId())))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("FALLIDO")).andExpect(jsonPath("$.ultimoError").value(containsString("401")))
                .andExpect(jsonPath("$.urlWaMe").exists()).andExpect(jsonPath("$.intentos").value(1)));
        long id = ((Number) JsonPath.read(r, "$.id")).longValue();
        // 500 y luego 200: el adaptador reintenta con backoff y entrega
        META.programar(500);
        POST("/api/compartir/" + id + "/reenviar", vet.bearer(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO")).andExpect(jsonPath("$.intentos").value(2))
                .andExpect(jsonPath("$.ultimoError").doesNotExist());
    }

    @Test
    void auxiliarHabilitadoEnviaResultadosConVeterinarioResponsable() throws Exception {
        Usuario vet = vetConTarjeta("TP-22");
        Usuario aux = usuario("AUXILIAR");
        CasoB c = casoB(false, true, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        Map<String, Object> formula = mapa("pacienteId", c.pacienteId(), "canal", "WHATSAPP",
                "documentos", List.of(mapa("tipo", "FORMULA", "id", q.recetaId())));
        POST("/api/compartir", aux.bearer(), formula).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("responsable")));
        formula.put("veterinarioResponsableId", aux.id());
        POST("/api/compartir", aux.bearer(), formula).andExpect(status().isUnprocessableEntity());
        formula.put("veterinarioResponsableId", vet.id());
        POST("/api/compartir", aux.bearer(), formula).andExpect(status().isCreated()).andExpect(jsonPath("$.responsableId").value(vet.id()));
        // el auxiliar nunca envia procedimientos ni resumenes, ni revoca
        POST("/api/compartir", aux.bearer(), mapa("pacienteId", c.pacienteId(), "canal", "WHATSAPP", "veterinarioResponsableId", vet.id(),
                "documentos", List.of(mapa("tipo", "RESUMEN_CONSULTA", "id", q.consultaId())))).andExpect(status().isForbidden());
        GET("/api/compartir/historial?pacienteId=" + c.pacienteId(), aux.bearer()).andExpect(status().isOk());
        // recepcion sigue sin acceso
        POST("/api/compartir", usuario("RECEPCION").bearer(), formula).andExpect(status().isForbidden());
    }
}
