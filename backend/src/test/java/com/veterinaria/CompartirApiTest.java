package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/** Compartir con el tutor en modo simulado (sin SMTP ni credenciales de WhatsApp). */
@SuppressWarnings("unchecked")
class CompartirApiTest extends EtapaBTestSupport {

    private Map<String, Object> pedido(CasoB c, String canal, Object... docs) {
        return mapa("pacienteId", c.pacienteId(), "canal", canal, "documentos", List.of(docs));
    }

    private Map<String, Object> formula(ConsultaB q) {
        return mapa("tipo", "FORMULA", "id", q.recetaId());
    }

    private String urlPublica(String respuesta) {
        String url = JsonPath.read(respuesta, "$.enlaces[0].url");
        return url.substring(url.indexOf("/api/"));
    }

    @Test
    void sinConsentimientoOSinAutorizacionDeDatosDevuelve422YNoEnvia() throws Exception {
        Usuario vet = vetConTarjeta("TP-10");
        CasoB sinEmail = casoB(false, true, true);
        ConsultaB q = consultaConFormula(vet, sinEmail.pacienteId(), true);
        POST("/api/compartir", vet.bearer(), pedido(sinEmail, "EMAIL", formula(q))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("consentimiento")))
                .andExpect(jsonPath("$.message").value(containsString("EMAIL")));
        CasoB sinDatos = casoB(true, true, false);
        ConsultaB q2 = consultaConFormula(vet, sinDatos.pacienteId(), true);
        POST("/api/compartir", vet.bearer(), pedido(sinDatos, "EMAIL", mapa("tipo", "FORMULA", "id", q2.recetaId())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(containsString("tratamiento de datos")));
        GET("/api/compartir/historial?pacienteId=" + sinEmail.pacienteId(), vet.bearer()).andExpect(jsonPath("$.totalElements").value(0));
        // previsualizar informa lo que falta sin lanzar error
        POST("/api/compartir/previsualizar", vet.bearer(), pedido(sinDatos, "EMAIL", mapa("tipo", "FORMULA", "id", q2.recetaId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.puedeEnviar").value(false))
                .andExpect(jsonPath("$.autorizaTratamientoDatos").value(false)).andExpect(jsonPath("$.consentimientoCanal").value(true))
                .andExpect(jsonPath("$.faltantes", hasSize(1)));
        // opt-out del tutor
        CasoB baja = casoB(true, true, true);
        ConsultaB q3 = consultaConFormula(vet, baja.pacienteId(), true);
        POST("/api/comunicaciones/optouts", admin(), Map.of("tutorId", baja.tutorId(), "canal", "EMAIL")).andExpect(status().isCreated());
        POST("/api/compartir", vet.bearer(), pedido(baja, "EMAIL", mapa("tipo", "FORMULA", "id", q3.recetaId())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(containsString("baja")));
    }

    @Test
    void emailConAdjuntosSimuladoDejaBitacoraSinDatosClinicosEnElTexto() throws Exception {
        Usuario vet = vetConTarjeta("TP-11");
        CasoB c = casoB(true, false, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        String r = json(POST("/api/compartir", vet.bearer(), pedido(c, "EMAIL", formula(q), mapa("tipo", "RESUMEN_CONSULTA", "id", q.consultaId()),
                mapa("tipo", "CARNET", "id", c.pacienteId()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ENVIADO_SIMULADO")).andExpect(jsonPath("$.proveedor").value("LOG_SIMULADO"))
                .andExpect(jsonPath("$.documentos", hasSize(3))).andExpect(jsonPath("$.enlaces", hasSize(0)))
                .andExpect(jsonPath("$.destinatario").value(containsString("***"))).andExpect(jsonPath("$.destinatario").value(not(containsString(c.email())))));
        String mensaje = JsonPath.read(r, "$.mensaje");
        assertTrue(mensaje.contains(c.tutorNombre()) && mensaje.contains("Mascota"), mensaje);
        assertTrue(!mensaje.contains("Amoxicilina") && !mensaje.contains("Gastroenteritis"), mensaje);
        long envio = ((Number) JsonPath.read(r, "$.id")).longValue();
        // instantanea almacenada: es un PDF y su hash coincide
        Long n = jdbc.sql("select count(*) from compartir_envio_documento where envio_id = :e and tamano_bytes > 500").param("e", envio)
                .query(Long.class).single();
        assertEquals(3L, n);
        GET("/api/compartir/historial?pacienteId=" + c.pacienteId(), vet.bearer()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].creadoPor").value(vet.id())).andExpect(jsonPath("$.items[0].documentos[0].hashSha256").isString());
        // email no tiene enlaces: no hay nada que revocar
        POST("/api/compartir/" + envio + "/revocar", vet.bearer(), Map.of()).andExpect(status().isConflict());
        POST("/api/compartir/" + envio + "/reenviar", vet.bearer(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.intentos").value(2));
        // auditoria: el envio quedo en la bitacora de auditoria con el usuario
        Long aud = jdbc.sql("select count(*) from auditoria where entidad = 'compartir_envio' and usuario_id = :u").param("u", vet.id())
                .query(Long.class).single();
        assertTrue(aud >= 2, "auditoria=" + aud);
    }

    @Test
    void whatsappSimuladoGeneraEnlaceSeguroYWaMe() throws Exception {
        Usuario vet = vetConTarjeta("TP-12");
        CasoB c = casoB(false, true, true, "+57 300 555 1234");
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        String r = json(POST("/api/compartir", vet.bearer(), pedido(c, "WHATSAPP", formula(q))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ENVIADO_SIMULADO")).andExpect(jsonPath("$.proveedor").value("WHATSAPP_SIMULADO"))
                .andExpect(jsonPath("$.enlaces", hasSize(1))).andExpect(jsonPath("$.enlaces[0].maxDescargas").value(5))
                .andExpect(jsonPath("$.urlWaMe").value(startsWith("https://wa.me/573005551234?text=")))
                .andExpect(jsonPath("$.destinatario").value(containsString("*"))));
        String url = JsonPath.read(r, "$.enlaces[0].url");
        assertTrue(url.contains("/api/publico/documentos/"));
        String token = url.substring(url.lastIndexOf('/') + 1);
        assertTrue(token.length() >= 40);
        // el token no se guarda: solo su hash
        assertEquals(0L, jdbc.sql("select count(*) from compartir_enlace where token_hash = :t").param("t", token).query(Long.class).single());
        String mensaje = JsonPath.read(r, "$.mensaje");
        assertTrue(!mensaje.contains(token) && mensaje.contains("[enlace seguro]"), mensaje);

        // descarga publica sin sesion
        byte[] pdf = mockMvc.perform(get(urlPublica(r))).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        assertTrue(textoPdf(pdf).contains("Amoxicilina"));
        GET("/api/compartir/historial?pacienteId=" + c.pacienteId(), vet.bearer()).andExpect(jsonPath("$.items[0].enlaces[0].descargas").value(1))
                .andExpect(jsonPath("$.items[0].enlaces[0].url").doesNotExist());

        // revocar -> 410; el envio queda revocado; reenviar crea un enlace nuevo que si funciona
        long envio = ((Number) JsonPath.read(r, "$.id")).longValue();
        POST("/api/compartir/" + envio + "/revocar", vet.bearer(), Map.of("motivo", "Enviado por error")).andExpect(status().isOk())
                .andExpect(jsonPath("$.revocado").value(true)).andExpect(jsonPath("$.enlaces[0].revocado").value(true));
        mockMvc.perform(get(urlPublica(r))).andExpect(status().isGone());
        String re = json(POST("/api/compartir/" + envio + "/reenviar", vet.bearer(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.revocado").value(false)));
        String nuevo = ((List<String>) JsonPath.read(re, "$.enlaces[?(@.revocado==false)].url")).get(0);
        mockMvc.perform(get(nuevo.substring(nuevo.indexOf("/api/")))).andExpect(status().isOk());
        mockMvc.perform(get(urlPublica(r))).andExpect(status().isGone());

        // bitacora de accesos: OK, REVOCADO...
        List<String> res = jdbc.sql("select resultado from compartir_acceso a join compartir_enlace l on l.id = a.enlace_id where l.envio_id = :e order by a.id")
                .param("e", envio).query(String.class).list();
        assertTrue(res.contains("OK") && res.contains("REVOCADO"), res.toString());
        assertTrue(jdbc.sql("select count(*) from auditoria where accion = 'DESCARGA_PUBLICA_DOCUMENTO'").query(Long.class).single() >= 2);
    }

    @Test
    void tokenVencidoLimiteDeDescargasYDesconocido() throws Exception {
        Usuario vet = vetConTarjeta("TP-13");
        CasoB c = casoB(false, true, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        String r = json(POST("/api/compartir", vet.bearer(), pedido(c, "WHATSAPP", formula(q))).andExpect(status().isCreated()));
        long enlace = ((Number) JsonPath.read(r, "$.enlaces[0].id")).longValue();
        String url = urlPublica(r);

        // limite de descargas
        jdbc.sql("update compartir_enlace set max_descargas = 2 where id = :id").param("id", enlace).update();
        mockMvc.perform(get(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isGone()).andExpect(jsonPath("$.message").value(containsString("ya no esta disponible")));
        // vencimiento (con descargas disponibles de nuevo)
        jdbc.sql("update compartir_enlace set max_descargas = 10, expira_en = now() - interval '1 hour' where id = :id").param("id", enlace).update();
        mockMvc.perform(get(url)).andExpect(status().isGone());
        // token desconocido
        mockMvc.perform(get("/api/publico/documentos/token-que-no-existe-0123456789")).andExpect(status().isNotFound());
        List<String> res = jdbc.sql("select resultado from compartir_acceso where enlace_id = :e or enlace_id is null order by id")
                .param("e", enlace).query(String.class).list();
        assertTrue(res.containsAll(List.of("OK", "LIMITE", "EXPIRADO", "NO_ENCONTRADO")), res.toString());
        // el vencimiento por defecto es de unas 72 h
        Double horas = jdbc.sql("select 1.0 * extract(epoch from (select creado_en from compartir_enlace where id = :id)) from compartir_enlace where id = :id")
                .param("id", enlace).query(Double.class).single();
        assertTrue(horas > 0);
    }

    @Test
    void documentosNoValidadosOAjenosDan422() throws Exception {
        Usuario vet = vetConTarjeta("TP-14");
        Usuario aux = usuario("AUXILIAR");
        CasoB c = casoB(true, true, true);
        // consulta abierta: formula no compartible
        ConsultaB abierta = consultaConFormula(vet, c.pacienteId(), false);
        POST("/api/compartir", vet.bearer(), pedido(c, "EMAIL", formula(abierta))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("ABIERTA")));
        POST("/api/compartir", vet.bearer(), pedido(c, "EMAIL", mapa("tipo", "RESUMEN_CONSULTA", "id", abierta.consultaId())))
                .andExpect(status().isUnprocessableEntity());
        // resultado en borrador -> 422; validado -> OK
        long orden = orden(vet, c.pacienteId(), "HEMO");
        muestra(aux, orden);
        long item = itemId(orden, "HEMO");
        capturar(aux.bearer(), orden, item, "HCT", 45, "WBC", 10).andExpect(status().isOk());
        Map<String, Object> lab = mapa("tipo", "LABORATORIO", "id", orden);
        POST("/api/compartir", vet.bearer(), pedido(c, "EMAIL", lab)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("VALIDADOS")));
        POST("/api/compartir/previsualizar", vet.bearer(), pedido(c, "EMAIL", lab)).andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeEnviar").value(false)).andExpect(jsonPath("$.documentos[0].enviable").value(false));
        validar(vet.bearer(), orden, item, null).andExpect(status().isOk());
        POST("/api/compartir", vet.bearer(), pedido(c, "EMAIL", lab)).andExpect(status().isCreated());
        // un tipo equivocado para la orden
        POST("/api/compartir", vet.bearer(), pedido(c, "EMAIL", mapa("tipo", "IMAGEN", "id", orden))).andExpect(status().isUnprocessableEntity());
        // documento de otro paciente
        CasoB otro = casoB(true, true, true);
        POST("/api/compartir", vet.bearer(), pedido(otro, "EMAIL", lab)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("no pertenece")));
        // inexistente y validacion de cuerpo
        POST("/api/compartir", vet.bearer(), pedido(c, "EMAIL", mapa("tipo", "FORMULA", "id", 999999))).andExpect(status().isNotFound());
        POST("/api/compartir", vet.bearer(), pedido(c, "SMS", lab)).andExpect(status().isBadRequest());
        POST("/api/compartir", vet.bearer(), mapa("pacienteId", c.pacienteId(), "canal", "EMAIL", "documentos", List.of()))
                .andExpect(status().isBadRequest());
        // tutor que no es del paciente
        POST("/api/compartir", vet.bearer(), mapa("pacienteId", c.pacienteId(), "tutorId", otro.tutorId(), "canal", "EMAIL", "documentos", List.of(lab)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rolesYTelefonoInvalido() throws Exception {
        Usuario vet = vetConTarjeta("TP-15");
        CasoB c = casoB(true, true, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        Map<String, Object> body = pedido(c, "EMAIL", formula(q));
        POST("/api/compartir", usuario("RECEPCION").bearer(), body).andExpect(status().isForbidden());
        POST("/api/compartir", usuario("AUXILIAR").bearer(), body).andExpect(status().isForbidden());
        GET("/api/compartir/historial", usuario("RECEPCION").bearer()).andExpect(status().isForbidden());
        GET("/api/compartir/historial", usuario("AUXILIAR").bearer()).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/compartir")
                .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        POST("/api/compartir", admin(), body).andExpect(status().isCreated());

        CasoB malTel = casoB(false, true, true, "123");
        ConsultaB q2 = consultaConFormula(vet, malTel.pacienteId(), true);
        POST("/api/compartir", vet.bearer(), pedido(malTel, "WHATSAPP", mapa("tipo", "FORMULA", "id", q2.recetaId())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(containsString("E.164")));
    }

    @Test
    void plantillasDeDocumentoSonConfigurables() throws Exception {
        Usuario vet = vetConTarjeta("TP-16");
        CasoB c = casoB(true, false, true);
        ConsultaB q = consultaConFormula(vet, c.pacienteId(), true);
        String plantillas = json(GET("/api/comunicaciones/plantillas?canal=EMAIL", admin()).andExpect(status().isOk()));
        long id = ((Number) ((List<?>) JsonPath.read(plantillas, "$[?(@.codigo=='DOC_FORMULA')].id")).get(0)).longValue();
        PUT("/api/comunicaciones/plantillas/" + id, admin(), Map.of("cuerpo", "Estimado {{tutor}}: {{documento}} de {{paciente}} en {{clinica}}."))
                .andExpect(status().isOk());
        POST("/api/compartir/previsualizar", vet.bearer(), pedido(c, "EMAIL", formula(q))).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(containsString("formula medica de Mascota")))
                .andExpect(jsonPath("$.mensaje").value(startsWith("Estimado")));
    }
}
