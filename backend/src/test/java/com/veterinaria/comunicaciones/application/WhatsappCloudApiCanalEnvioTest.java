package com.veterinaria.comunicaciones.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.veterinaria.comunicaciones.application.CanalEnvio.DocumentoEnlace;
import com.veterinaria.comunicaciones.application.CanalEnvio.EnvioFallidoException;
import com.veterinaria.comunicaciones.application.CanalEnvio.Mensaje;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WhatsappCloudApiCanalEnvioTest {

    private static final JsonMapper M = JsonMapper.builder().build();

    private WhatsappCloudApiCanalEnvio adaptador(WhatsappFalso f, String token, String plantilla, int reintentos) {
        return new WhatsappCloudApiCanalEnvio(new JdkWhatsappHttpClient(), token, token.isEmpty() ? "" : "98765", "v21.0", f.baseUrl(),
                plantilla, "es", "tutor,paciente,enlace", false, "57", reintentos, 1);
    }

    private static Mensaje mensaje(String destino) {
        return new Mensaje(destino, null, "Hola Ana, te compartimos el informe de Rocky: https://x/api/publico/documentos/abc",
                List.of(), List.of(new DocumentoEnlace("informe.pdf", "https://x/api/publico/documentos/abc")),
                Map.of("tutor", "Ana", "paciente", "Rocky", "enlace", "https://x/api/publico/documentos/abc"));
    }

    @Test
    void sinCredencialesEsSimulado() {
        try (WhatsappFalso f = new WhatsappFalso()) {
            var a = adaptador(f, "", "", 2);
            assertFalse(a.configurado());
            var r = a.enviar(mensaje("3001234567"));
            assertTrue(r.simulado());
            assertEquals("WHATSAPP_SIMULADO", r.proveedor());
            assertTrue(f.peticiones.isEmpty());
            assertThrows(EnvioFallidoException.class, () -> a.enviar(mensaje("123")));
        }
    }

    @Test
    void enviaMensajeDeDocumentoPorUrlConBearer() {
        try (WhatsappFalso f = new WhatsappFalso()) {
            var r = adaptador(f, "TOKEN123", "", 2).enviar(mensaje("300 123 4567"));
            assertFalse(r.simulado());
            assertEquals("WHATSAPP_CLOUD_API", r.proveedor());
            assertEquals("wamid.TEST1", r.mensajeId());
            var p = f.peticiones.get(0);
            assertEquals("POST", p.metodo());
            assertEquals("/v21.0/98765/messages", p.ruta());
            assertEquals("Bearer TOKEN123", p.autorizacion());
            JsonNode j = M.readTree(p.cuerpo());
            assertEquals("whatsapp", j.path("messaging_product").asString());
            assertEquals("573001234567", j.path("to").asString());
            assertEquals("document", j.path("type").asString());
            assertEquals("https://x/api/publico/documentos/abc", j.path("document").path("link").asString());
            assertEquals("informe.pdf", j.path("document").path("filename").asString());
            assertTrue(j.path("document").path("caption").asString().contains("Rocky"));
        }
    }

    @Test
    void enviaMensajeDePlantillaAprobada() {
        try (WhatsappFalso f = new WhatsappFalso()) {
            adaptador(f, "T", "aviso_documento", 0).enviar(mensaje("+573001234567"));
            JsonNode j = M.readTree(f.peticiones.get(0).cuerpo());
            assertEquals("template", j.path("type").asString());
            assertEquals("aviso_documento", j.path("template").path("name").asString());
            assertEquals("es", j.path("template").path("language").path("code").asString());
            JsonNode ps = j.path("template").path("components").path(0).path("parameters");
            assertEquals("body", j.path("template").path("components").path(0).path("type").asString());
            assertEquals("Ana", ps.path(0).path("text").asString());
            assertEquals("Rocky", ps.path(1).path("text").asString());
            assertEquals("https://x/api/publico/documentos/abc", ps.path(2).path("text").asString());
        }
    }

    @Test
    void reintentaConBackoffEnErroresTransitoriosYFallaEnLosPermanentes() {
        try (WhatsappFalso f = new WhatsappFalso()) {
            f.programar(503, 429);
            var r = adaptador(f, "T", "", 2).enviar(mensaje("3001234567"));
            assertEquals(3, f.peticiones.size());
            assertFalse(r.simulado());
        }
        try (WhatsappFalso f = new WhatsappFalso()) {
            f.programar(500, 500, 500);
            var ex = assertThrows(EnvioFallidoException.class, () -> adaptador(f, "T", "", 2).enviar(mensaje("3001234567")));
            assertEquals(3, f.peticiones.size());
            assertTrue(ex.getMessage().contains("500") && ex.getMessage().contains("Error simulado"), ex.getMessage());
        }
        try (WhatsappFalso f = new WhatsappFalso()) {
            f.programar(400);
            var ex = assertThrows(EnvioFallidoException.class, () -> adaptador(f, "T", "", 3).enviar(mensaje("3001234567")));
            assertEquals(1, f.peticiones.size());
            assertTrue(ex.getMessage().contains("400"));
        }
    }

    @Test
    void noEsCanalDeRecordatorios() {
        try (WhatsappFalso f = new WhatsappFalso()) {
            assertFalse(adaptador(f, "", "", 0).admiteRecordatorios());
        }
    }
}
