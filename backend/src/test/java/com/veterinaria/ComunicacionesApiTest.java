package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class ComunicacionesApiTest extends Fase3TestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Solo las reglas que cada prueba crea deben estar activas. */
    @BeforeEach
    void reglasLimpias() {
        jdbc.sql("update regla_recordatorio set activo = false").update();
        // aislar: lo creado por pruebas anteriores no debe generar recordatorios en esta
        jdbc.sql("update inmunizacion set estado = 'ANULADA' where estado = 'APLICADA'").update();
        jdbc.sql("update desparasitacion set estado = 'ANULADA' where estado = 'APLICADA'").update();
        jdbc.sql("update cita set activo = false").update();
    }

    private long plantilla(String cuerpo) throws Exception {
        return idDe(POST("/api/comunicaciones/plantillas", admin(), Map.of("codigo", "T" + SEQ.incrementAndGet() + "-" + System.nanoTime(),
                "canal", "EMAIL", "asunto", "Aviso de {{paciente}}", "cuerpo", cuerpo)).andExpect(status().isCreated()));
    }

    private long regla(String evento, int offset, long plantilla) throws Exception {
        return idDe(POST("/api/comunicaciones/reglas", admin(), Map.of("codigo", "R" + SEQ.incrementAndGet() + "-" + System.nanoTime(),
                "evento", evento, "diasOffset", offset, "canal", "EMAIL", "plantillaId", plantilla))
                .andExpect(status().isCreated()));
    }

    private void aplicarVacuna(long paciente, int diasHastaProxima) throws Exception {
        Usuario vet = usuario("VETERINARIO");
        POST("/api/pacientes/" + paciente + "/inmunizaciones", vet.bearer(), aplicacionVacuna(vacunaId("CAN-RAB"), hoy(),
                Map.of("proximaFecha", hoy().plusDays(diasHastaProxima).toString()))).andExpect(status().isCreated());
    }

    private String recordatorios(long paciente) throws Exception {
        return json(GET("/api/comunicaciones/recordatorios?pacienteId=" + paciente, admin()).andExpect(status().isOk()));
    }

    @Test
    void jobGeneraEnviaSimuladoSinDuplicarYDejaBitacora() throws Exception {
        long regla = regla("VACUNA_PROXIMA", -7, plantilla("Hola {{tutor}}, a {{paciente}} le toca {{vacuna}} el {{fecha}}. {{clinica}}"));
        Caso c = caso(true, null);
        aplicarVacuna(c.pacienteId(), 5);

        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.generados").value(1)).andExpect(jsonPath("$.simulados").value(1))
                .andExpect(jsonPath("$.fallidos").value(0));
        String lista = recordatorios(c.pacienteId());
        Assertions.assertEquals("1", primerValor(lista, "$.totalElements"));
        Assertions.assertEquals("ENVIADO_SIMULADO", primerValor(lista, "$.items[0].estado"));
        Assertions.assertEquals("VACUNA_PROXIMA", primerValor(lista, "$.items[0].tipo"));
        Assertions.assertEquals("inmunizacion", primerValor(lista, "$.items[0].referenciaTabla"));
        Assertions.assertEquals(String.valueOf(regla), primerValor(lista, "$.items[0].reglaId"));
        long recId = ((Number) JsonPath.read(lista, "$.items[0].id")).longValue();

        String bitacora = json(GET("/api/comunicaciones/recordatorios/" + recId + "/bitacora", admin())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].estado").value("ENVIADO_SIMULADO"))
                .andExpect(jsonPath("$[0].destinatario").value(c.email()))
                .andExpect(jsonPath("$[0].proveedor").value("LOG_SIMULADO"))
                .andExpect(jsonPath("$[0].asunto").value("Aviso de " + primerValor(json(GET("/api/pacientes/" + c.pacienteId(), admin())), "$.nombre")))
                .andExpect(jsonPath("$[0].cuerpo").value(containsString("Antirrabica canina")))
                .andExpect(jsonPath("$[0].cuerpo").value(containsString(hoy().plusDays(5).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))))
                .andExpect(jsonPath("$[0].cuerpo").value(containsString("optout?token="))));
        Assertions.assertFalse(bitacora.contains("{{"));

        // idempotencia: segunda ejecucion no crea ni reenvia
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.generados").value(0)).andExpect(jsonPath("$.yaExistian").value(1))
                .andExpect(jsonPath("$.simulados").value(0));
        Assertions.assertEquals("1", primerValor(recordatorios(c.pacienteId()), "$.totalElements"));
        GET("/api/comunicaciones/envios?tutorId=" + c.tutorId(), admin()).andExpect(jsonPath("$.totalElements").value(1));
        // el unico registro no se puede cancelar: ya salio
        POST("/api/comunicaciones/recordatorios/" + recId + "/cancelar", admin(), Map.of()).andExpect(status().isConflict());
    }

    @Test
    void sinConsentimientoNoSeCreaYConConsentimientoPosteriorSi() throws Exception {
        regla("VACUNA_PROXIMA", -7, plantilla("Hola {{tutor}}"));
        Caso c = caso(false, null);
        aplicarVacuna(c.pacienteId(), 3);
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.generados").value(0));
        Assertions.assertEquals("0", primerValor(recordatorios(c.pacienteId()), "$.totalElements"));
        GET("/api/comunicaciones/envios?tutorId=" + c.tutorId(), admin()).andExpect(jsonPath("$.totalElements").value(0));
        Assertions.assertTrue(((Number) JsonPath.read(json(POST("/api/comunicaciones/ejecutar", admin(), Map.of())),
                "$.omitidosSinConsentimiento")).intValue() >= 1);

        // el tutor da su consentimiento: el siguiente job si lo genera
        PUT("/api/tutores/" + c.tutorId(), admin(), Map.of("tipoDocumento", "CC",
                "numeroDocumento", primerValor(json(GET("/api/tutores/" + c.tutorId(), admin())), "$.numeroDocumento"),
                "nombres", "Tutor", "email", c.email(), "aceptaEmail", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aceptaEmail").value(true));
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.generados").value(1));
        Assertions.assertEquals("ENVIADO_SIMULADO", primerValor(recordatorios(c.pacienteId()), "$.items[0].estado"));
    }

    @Test
    void optOutPorTokenYManualImpideNuevosRecordatorios() throws Exception {
        long plantilla = plantilla("Hola {{tutor}}, vacuna {{vacuna}}");
        regla("VACUNA_PROXIMA", -7, plantilla);
        Caso c = caso(true, null);
        aplicarVacuna(c.pacienteId(), 2);
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.generados").value(1));
        long recId = ((Number) JsonPath.read(recordatorios(c.pacienteId()), "$.items[0].id")).longValue();
        String cuerpo = primerValor(json(GET("/api/comunicaciones/recordatorios/" + recId + "/bitacora", admin())), "$[0].cuerpo");
        Matcher m = Pattern.compile("token=(\\S+)").matcher(cuerpo);
        Assertions.assertTrue(m.find(), cuerpo);
        String token = m.group(1);

        // endpoint publico (sin Authorization)
        mockMvc.perform(get("/api/comunicaciones/optout").param("token", token)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html")).andExpect(content().string(containsString("Listo")));
        mockMvc.perform(get("/api/comunicaciones/optout").param("token", token)).andExpect(status().isOk()); // idempotente
        mockMvc.perform(get("/api/comunicaciones/optout").param("token", token + "x")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/comunicaciones/optout").param("token", "basura")).andExpect(status().isBadRequest());
        GET("/api/comunicaciones/optouts?tutorId=" + c.tutorId(), admin()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].canal").value("EMAIL")).andExpect(jsonPath("$[0].categoria").value("TODAS"));

        // otra mascota del mismo tutor: ya no se le crea nada
        long otro = idDe(POST("/api/pacientes", admin(), Map.of("nombre", "Segunda", "especieId",
                primerId(json(GET("/api/catalogos/especies", admin())), "$[?(@.nombre=='Perro')].id"), "sexo", "M",
                "tutorIds", List.of(c.tutorId()), "tutorPrincipalId", c.tutorId())).andExpect(status().isCreated()));
        aplicarVacuna(otro, 2);
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.generados").value(0));
        Assertions.assertEquals("0", primerValor(recordatorios(otro), "$.totalElements"));

        // baja manual por recepcion y levantamiento
        Caso d = caso(true, null);
        Usuario recepcion = usuario("RECEPCION");
        long optId = idDe(POST("/api/comunicaciones/optouts", recepcion.bearer(), Map.of("tutorId", d.tutorId(), "motivo", "Lo pidio por telefono"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canal").value("TODOS")));
        aplicarVacuna(d.pacienteId(), 2);
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.generados").value(0));
        DELETE("/api/comunicaciones/optouts/" + optId, recepcion.bearer()).andExpect(status().isNoContent());
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.generados").value(1));
    }

    @Test
    void reintentosConLimiteCancelacionYReemplazoPorNuevaDosis() throws Exception {
        long plantilla = plantilla("Hola {{tutor}}");
        regla("VACUNA_PROXIMA", -7, plantilla);
        Caso c = caso(true, null);
        aplicarVacuna(c.pacienteId(), 4);
        // plantilla inactiva: el envio falla y queda PENDIENTE para reintento
        PUT("/api/comunicaciones/plantillas/" + plantilla, admin(), Map.of("activo", false)).andExpect(status().isOk());
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.generados").value(1))
                .andExpect(jsonPath("$.reintentosPendientes").value(1)).andExpect(jsonPath("$.fallidos").value(0));
        String r = recordatorios(c.pacienteId());
        long recId = ((Number) JsonPath.read(r, "$.items[0].id")).longValue();
        Assertions.assertEquals("PENDIENTE", primerValor(r, "$.items[0].estado"));
        Assertions.assertEquals("1", primerValor(r, "$.items[0].intentos"));
        Assertions.assertEquals("Plantilla no disponible", primerValor(r, "$.items[0].ultimoError"));
        // aun no toca reintentar (backoff): nada cambia
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.reintentosPendientes").value(0));
        // adelantamos el reintento: 2do y 3er intento -> FALLIDO al llegar al limite (3)
        for (int i = 0; i < 2; i++) {
            jdbc.sql("update recordatorio set programado_para = now() - interval '1 minute' where id = :id").param("id", recId).update();
            POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(status().isOk());
        }
        Assertions.assertEquals("FALLIDO", primerValor(recordatorios(c.pacienteId()), "$.items[0].estado"));
        GET("/api/comunicaciones/recordatorios/" + recId + "/bitacora", admin()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].estado").value("FALLIDO"));
        // una vez FALLIDO no se reintenta ni se duplica
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.generados").value(0));

        // cancelar un pendiente, y el reemplazo por una nueva dosis cancela el pendiente anterior (evento)
        long p2 = plantilla("Hola {{tutor}} 2");
        jdbc.sql("update regla_recordatorio set activo = false").update();
        regla("VACUNA_PROXIMA", -7, p2);
        PUT("/api/comunicaciones/plantillas/" + p2, admin(), Map.of("activo", false)).andExpect(status().isOk());
        Caso d = caso(true, null);
        aplicarVacuna(d.pacienteId(), 4);
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(status().isOk());
        long pend = ((Number) JsonPath.read(recordatorios(d.pacienteId()), "$.items[0].id")).longValue();
        aplicarVacuna(d.pacienteId(), 300); // nueva dosis: la anterior deja de ser la vigente
        GET("/api/comunicaciones/recordatorios/" + pend, admin()).andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.ultimoError").value(containsString("Reemplazado")));

        Caso e = caso(true, null);
        aplicarVacuna(e.pacienteId(), 4);
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(status().isOk());
        long pend2 = ((Number) JsonPath.read(recordatorios(e.pacienteId()), "$.items[0].id")).longValue();
        Usuario recepcion = usuario("RECEPCION");
        POST("/api/comunicaciones/recordatorios/" + pend2 + "/cancelar", recepcion.bearer(), Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
        POST("/api/comunicaciones/recordatorios/" + pend2 + "/cancelar", recepcion.bearer(), Map.of())
                .andExpect(status().isConflict());
        GET("/api/comunicaciones/recordatorios?estado=CANCELADO&pacienteId=" + e.pacienteId(), recepcion.bearer())
                .andExpect(jsonPath("$.totalElements").value(1));
        POST("/api/comunicaciones/recordatorios/999999/cancelar", admin(), Map.of()).andExpect(status().isNotFound());
        // anular la aplicacion cancela su recordatorio pendiente
        Caso f = caso(true, null);
        aplicarVacuna(f.pacienteId(), 4);
        POST("/api/comunicaciones/ejecutar", admin(), Map.of());
        long pend3 = ((Number) JsonPath.read(recordatorios(f.pacienteId()), "$.items[0].id")).longValue();
        long inm = primerId(json(GET("/api/pacientes/" + f.pacienteId() + "/inmunizaciones", admin())), "$[*].id");
        POST("/api/pacientes/" + f.pacienteId() + "/inmunizaciones/" + inm + "/anular", admin(), Map.of("motivo", "error"))
                .andExpect(status().isOk());
        GET("/api/comunicaciones/recordatorios/" + pend3, admin()).andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.ultimoError").value(containsString("anulada")));
    }

    @Test
    void recordatorioDeCitaYDesparasitacion() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        regla("CITA", -1, plantilla("Cita de {{paciente}} el {{fecha}} a las {{hora}} en {{sede}}"));
        long despar = plantilla("Desparasitar a {{paciente}} ({{fecha}})");
        regla("DESPARASITACION_PROXIMA", -7, despar);
        Caso c = caso(true, null);
        crearCita(admin(), c.pacienteId(), vet.id(), 1, "10:00", Map.of()).andExpect(status().isCreated());
        POST("/api/pacientes/" + c.pacienteId() + "/inmunizaciones", vet.bearer(), Map.of("tipo", "ANTIPARASITARIO",
                "antiparasitarioId", antiparasitarioId("ANT-ISOX"), "fechaAplicacion", hoy().minusDays(25).toString()))
                .andExpect(status().isCreated());
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.generados").value(2));
        String lista = recordatorios(c.pacienteId());
        Assertions.assertEquals("2", primerValor(lista, "$.totalElements"));
        List<String> tipos = JsonPath.read(lista, "$.items[*].tipo");
        Assertions.assertTrue(tipos.containsAll(List.of("CITA", "DESPARASITACION_PROXIMA")), tipos.toString());
        long cita = primerId(lista, "$.items[?(@.tipo=='CITA')].id");
        GET("/api/comunicaciones/recordatorios/" + cita + "/bitacora", admin())
                .andExpect(jsonPath("$[0].cuerpo").value(containsString("10:00")));
        // una regla de WhatsApp (sin adaptador) se ignora y se reporta
        long wa = idDe(POST("/api/comunicaciones/plantillas", admin(), Map.of("codigo", "WA-" + System.nanoTime(), "canal", "WHATSAPP",
                "cuerpo", "Hola {{tutor}}")).andExpect(status().isCreated()));
        POST("/api/comunicaciones/reglas", admin(), Map.of("codigo", "RWA-" + System.nanoTime(), "evento", "VACUNA_PROXIMA",
                "diasOffset", -7, "canal", "WHATSAPP", "plantillaId", wa)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.canalDisponible").value(false));
        POST("/api/comunicaciones/ejecutar", admin(), Map.of()).andExpect(jsonPath("$.reglasSinCanal").value(1));
    }

    @Test
    void plantillasYReglasCrudValidacionYRoles() throws Exception {
        Usuario recepcion = usuario("RECEPCION");
        Usuario vet = usuario("VETERINARIO");
        String codigo = "CRUD-" + System.nanoTime();
        Map<String, Object> body = Map.of("codigo", codigo, "canal", "EMAIL", "asunto", "Hola {{tutor}}",
                "cuerpo", "Vacuna {{vacuna}} de {{paciente}} el {{fecha}} {{hora}} {{clinica}} {{sede}}");
        POST("/api/comunicaciones/plantillas", recepcion.bearer(), body).andExpect(status().isForbidden());
        long id = idDe(POST("/api/comunicaciones/plantillas", admin(), body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.idioma").value("es")).andExpect(jsonPath("$.variables", hasSize(7))));
        POST("/api/comunicaciones/plantillas", admin(), body).andExpect(status().isConflict());
        POST("/api/comunicaciones/plantillas", admin(), Map.of("codigo", "BAD", "canal", "EMAIL", "cuerpo", "Hola {{mascota}}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(containsString("mascota")));
        POST("/api/comunicaciones/plantillas", admin(), Map.of("codigo", "BAD2", "canal", "FAX", "cuerpo", "x"))
                .andExpect(status().isBadRequest());
        PUT("/api/comunicaciones/plantillas/" + id, admin(), Map.of("cuerpo", "Solo {{tutor}}", "asunto", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variables", hasSize(1)))
                .andExpect(jsonPath("$.asunto").doesNotExist());
        GET("/api/comunicaciones/plantillas?canal=EMAIL", recepcion.bearer()).andExpect(status().isOk());
        GET("/api/comunicaciones/plantillas", vet.bearer()).andExpect(status().isForbidden());
        GET("/api/comunicaciones/plantillas/999999", admin()).andExpect(status().isNotFound());

        long regla = idDe(POST("/api/comunicaciones/reglas", admin(), Map.of("codigo", "RC-" + System.nanoTime(),
                "evento", "VACUNA_PROXIMA", "diasOffset", -3, "canal", "EMAIL", "plantillaId", id))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canalDisponible").value(true))
                .andExpect(jsonPath("$.plantillaCodigo").value(codigo)));
        PUT("/api/comunicaciones/reglas/" + regla, admin(), Map.of("diasOffset", -10, "activo", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diasOffset").value(-10))
                .andExpect(jsonPath("$.activo").value(false));
        // canal de la regla distinto al de la plantilla
        POST("/api/comunicaciones/reglas", admin(), Map.of("codigo", "RX-" + System.nanoTime(), "evento", "CITA",
                "diasOffset", -1, "canal", "WHATSAPP", "plantillaId", id)).andExpect(status().isUnprocessableEntity());
        POST("/api/comunicaciones/reglas", admin(), Map.of("codigo", "RY", "evento", "CITA", "diasOffset", -1, "canal", "EMAIL",
                "plantillaId", 999999)).andExpect(status().isNotFound());
        POST("/api/comunicaciones/reglas", recepcion.bearer(), Map.of("codigo", "RZ")).andExpect(status().isForbidden());
        GET("/api/comunicaciones/reglas?evento=VACUNA_PROXIMA", recepcion.bearer()).andExpect(status().isOk());

        // job manual solo ADMIN; recordatorios: RECEPCION si, VETERINARIO no
        POST("/api/comunicaciones/ejecutar", recepcion.bearer(), Map.of()).andExpect(status().isForbidden());
        POST("/api/comunicaciones/ejecutar", vet.bearer(), Map.of()).andExpect(status().isForbidden());
        GET("/api/comunicaciones/recordatorios", recepcion.bearer()).andExpect(status().isOk());
        GET("/api/comunicaciones/recordatorios", vet.bearer()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/comunicaciones/recordatorios")).andExpect(status().isUnauthorized());
        // semillas de V17 presentes (plantilla de vacuna y reglas)
        GET("/api/comunicaciones/reglas?evento=VACUNA_PROXIMA&activo=false", admin()).andExpect(jsonPath("$[?(@.codigo=='VAC_7D_EMAIL')]", hasSize(1)));
    }
}
